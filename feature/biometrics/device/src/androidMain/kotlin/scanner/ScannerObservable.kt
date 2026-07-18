package scanner

import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.State
import kotlinx.coroutines.flow.Flow

interface ScannerObservable {
    val state: Flow<State>

    val mode: Flow<Mode>

    fun reset()
}
