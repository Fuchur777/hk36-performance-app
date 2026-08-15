package nl.glcillustrious.hk36ttc.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.glcillustrious.hk36ttc.core.wb.AircraftProfile
import nl.glcillustrious.hk36ttc.core.wb.FuelTankType
import nl.glcillustrious.hk36ttc.core.wb.WbConstantsData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileEntity
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository

class ProfileListViewModel(repository: AircraftProfileRepository) : ViewModel() {

    val profiles: StateFlow<List<AircraftProfileEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(repository: AircraftProfileRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ProfileListViewModel(repository) as T
            }
        }
    }
}

/**
 * Why a profile field failed validation — typed rather than a literal message, since this
 * `ViewModel` has no `Context`/`Resources` to localize a string with. The app layer maps each
 * case to a localized string (see `ProfileEditScreen.kt`).
 */
enum class ProfileFieldError { REQUIRED, AFT_LIMIT_MUST_EXCEED_FORWARD }

/**
 * Form state for creating/editing one [AircraftProfile]. Numeric fields are whole-number
 * `Int` (kg/mm) driven by stepper controls, not free text — a stepper can never produce an
 * invalid number, so there's no numeric-parse validation left to do here, only the business
 * rule that the aft CG limit must be behind the forward one.
 */
data class ProfileFormState(
    val id: Long = 0,
    val registration: String = "",
    val emptyMassKg: Int,
    val emptyMassCgPositionMm: Int,
    val mtowKg: Int,
    val cgEnvelopeForwardLimitMm: Int,
    val cgEnvelopeAftLimitMm: Int,
    val fuelTankType: FuelTankType = FuelTankType.STANDARD_55L,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errors: Map<String, ProfileFieldError> = emptyMap()
) {
    fun toDomainOrNull(): AircraftProfile? {
        if (errors.isNotEmpty() || registration.isBlank()) return null
        return AircraftProfile(
            registration = registration.trim(),
            emptyMassKg = emptyMassKg.toDouble(),
            emptyMassCgPositionMm = emptyMassCgPositionMm.toDouble(),
            mtowKg = mtowKg.toDouble(),
            cgEnvelopeForwardLimitMm = cgEnvelopeForwardLimitMm.toDouble(),
            cgEnvelopeAftLimitMm = cgEnvelopeAftLimitMm.toDouble(),
            fuelTankType = fuelTankType
        )
    }

    companion object {
        /**
         * Starting values for a brand-new profile. These are NOT AFM-mandated numbers —
         * weight_balance_constants.json is explicit that empty mass/CG/envelope must come
         * from the owner's own Weighing Report — they're just plausible HK36 TTC ballpark
         * figures (informed by a real PH-1600 weighing) so the stepper doesn't start at a
         * meaningless 0 and force a long scroll. [defaultMtowKg] is the one value that IS
         * sourced from the loaded JSON rather than a literal here.
         */
        fun newProfileDefaults(defaultMtowKg: Int) = ProfileFormState(
            emptyMassKg = 600,
            emptyMassCgPositionMm = 400,
            mtowKg = defaultMtowKg,
            cgEnvelopeForwardLimitMm = 360,
            cgEnvelopeAftLimitMm = 420
        )
    }
}

class ProfileEditViewModel(
    private val repository: AircraftProfileRepository,
    profileId: Long,
    wbConstants: WbConstantsData
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileFormState.newProfileDefaults(defaultMtowKg = wbConstants.defaultMtowKg.roundToInt())
            .copy(id = profileId)
    )
    val state: StateFlow<ProfileFormState> = _state

    init {
        if (profileId != 0L) {
            viewModelScope.launch {
                repository.getById(profileId)?.let { entity ->
                    _state.value = ProfileFormState(
                        id = entity.id,
                        registration = entity.registration,
                        emptyMassKg = entity.emptyMassKg.roundToInt(),
                        emptyMassCgPositionMm = entity.emptyMassCgPositionMm.roundToInt(),
                        mtowKg = entity.mtowKg.roundToInt(),
                        cgEnvelopeForwardLimitMm = entity.cgEnvelopeForwardLimitMm.roundToInt(),
                        cgEnvelopeAftLimitMm = entity.cgEnvelopeAftLimitMm.roundToInt(),
                        fuelTankType = entity.fuelTankType
                    )
                }
            }
        }
    }

    fun update(transform: (ProfileFormState) -> ProfileFormState) {
        _state.value = validate(transform(_state.value))
    }

    private fun validate(form: ProfileFormState): ProfileFormState {
        val errors = mutableMapOf<String, ProfileFieldError>()
        if (form.registration.isBlank()) errors["registration"] = ProfileFieldError.REQUIRED
        if (form.cgEnvelopeForwardLimitMm >= form.cgEnvelopeAftLimitMm) {
            errors["cgEnvelopeAftLimitMm"] = ProfileFieldError.AFT_LIMIT_MUST_EXCEED_FORWARD
        }
        return form.copy(errors = errors)
    }

    fun save() {
        val current = _state.value
        val domain = current.toDomainOrNull() ?: return
        viewModelScope.launch {
            _state.value = current.copy(isLoading = true)
            repository.save(domain, current.id)
            _state.value = current.copy(isLoading = false, isSaved = true)
        }
    }

    companion object {
        fun factory(repository: AircraftProfileRepository, profileId: Long, wbConstants: WbConstantsData) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return ProfileEditViewModel(repository, profileId, wbConstants) as T
                }
            }
    }
}
