/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.disableflags.data.repository

import com.android.systemui.common.coroutine.ConflatedCallbackFlow.conflatedCallbackFlow
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.DisplayId
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.dagger.DisableFlagsRepositoryLog
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.disableflags.DisableFlagsLogger
import com.android.systemui.statusbar.disableflags.data.model.DisableFlagsModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Repository for the disable flags received from external systems. See [IStatusBar.disable]. */
@SysUISingleton
class DisableFlagsRepository
@Inject
constructor(
    commandQueue: CommandQueue,
    @DisplayId private val displayId: Int,
    @Application scope: CoroutineScope,
    @DisableFlagsRepositoryLog private val logBuffer: LogBuffer,
    private val disableFlagsLogger: DisableFlagsLogger,
) {
    val disableFlags: StateFlow<DisableFlagsModel> =
        conflatedCallbackFlow {
                val callback =
                    object : CommandQueue.Callbacks {
                        override fun disable(
                            displayId: Int,
                            state1: Int,
                            state2: Int,
                            animate: Boolean,
                        ) {
                            if (displayId != this@DisableFlagsRepository.displayId) {
                                return
                            }
                            trySend(DisableFlagsModel(state1, state2))
                        }
                    }
                commandQueue.addCallback(callback)
                awaitClose { commandQueue.removeCallback(callback) }
            }
            .distinctUntilChanged()
            .onEach { it.logChange(logBuffer, disableFlagsLogger) }
            // Use Eagerly because we always need to know about disable flags
            .stateIn(scope, SharingStarted.Eagerly, DisableFlagsModel())
}
