package com.tanda.attendance.ui.student

import com.tanda.attendance.domain.usecase.ObserveStudentAttendancePointsUsecase
import com.tanda.attendance.domain.usecase.RecordStudentAttendanceUsecase
import com.tanda.attendance.domain.usecase.RefreshStudentAttendancePointsUsecase
import com.tanda.biometrics.ui.fingerprint.Fingerprint
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object StudentAttendance {
    class Builder(scope: Scope) : UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<StudentAttendance>())
            scope.getKoin().loadModules(
                listOf(
                    module {
                        scope<StudentAttendance> {
                            scoped {
                                StudentAttendanceViewModel(
                                    dispatcher = get<Dispatcher>(),
                                    observePointsUsecase = get<ObserveStudentAttendancePointsUsecase>(),
                                    refreshPointsUsecase = get<RefreshStudentAttendancePointsUsecase>(),
                                )
                            }
                            scoped {
                                StudentAttendanceCaptureViewModel(
                                    dispatcher = get<Dispatcher>(),
                                    recordAttendanceUsecase = get<RecordStudentAttendanceUsecase>(),
                                )
                            }
                            factory<UiComponentProvider.Factory> {
                                UiBuilderFactory(
                                    listOf(
                                        this@Builder,
                                        Fingerprint.Builder(scope),
                                    )
                                )
                            }
                        }
                    }
                )
            )
            return scope
        }
    }
}
