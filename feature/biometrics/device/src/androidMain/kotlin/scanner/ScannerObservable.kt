package scanner

import com.tanda.biometrics.domain.model.Event
import com.tanda.biometrics.domain.model.State
import kotlinx.coroutines.flow.Flow

interface ScannerObservable {
    val state: Flow<State>

    val event: Flow<Event>
}
