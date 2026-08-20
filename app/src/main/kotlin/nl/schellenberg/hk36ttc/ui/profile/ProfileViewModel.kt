package nl.schellenberg.hk36ttc.ui.profile

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
import nl.schellenberg.hk36ttc.core.wb.AircraftProfile
import nl.schellenberg.hk36ttc.core.wb.FuelTankType
import nl.schellenberg.hk36ttc.core.wb.WbConstantsData
import nl.schellenberg.hk36ttc.data.local.AircraftProfileEntity
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository

class ProfileListViewModel(private val repository: AircraftProfileRepository) : ViewModel() {

    val profiles: StateFlow<List<AircraftProfileEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteProfile(profile: AircraftProfileEntity) {
        viewModelScope.launch {
            repository.deleteProfileCascade(profile)
        }
    }

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
    /** True once a save has been attempted while [registration] was blank — see
     * [nl.schellenberg.hk36ttc.ui.airfield.AirfieldFormState.saveAttempted], the same fix
     * for the same bug: tapping Save on a blank required field used to just do nothing. */
    val saveAttempted: Boolean = false
) {
    /**
     * Recomputed from the current fields every time, not stored — the old stored-map design
     * only refreshed inside [ProfileEditViewModel.update], so a save attempt on an untouched
     * fresh screen saw an empty, stale map (silent failure) while nudging an unrelated stepper
     * could make the registration error pop up before the pilot had even reached that field.
     *
     * [ProfileFieldError.REQUIRED] is gated on [saveAttempted] for that reason — an empty
     * required field shouldn't be flagged before the pilot has tried to submit it.
     * [ProfileFieldError.AFT_LIMIT_MUST_EXCEED_FORWARD] is not gated: it can only ever fire
     * from a value the pilot just set (the defaults are always internally consistent), so
     * showing it immediately while adjusting the steppers is useful, not premature.
     */
    val errors: Map<String, ProfileFieldError>
        get() {
            val result = mutableMapOf<String, ProfileFieldError>()
            if (saveAttempted && registration.isBlank()) result["registration"] = ProfileFieldError.REQUIRED
            if (cgEnvelopeForwardLimitMm >= cgEnvelopeAftLimitMm) {
                result["cgEnvelopeAftLimitMm"] = ProfileFieldError.AFT_LIMIT_MUST_EXCEED_FORWARD
            }
            return result
        }

    fun toDomainOrNull(): AircraftProfile? {
        if (registration.isBlank() || cgEnvelopeForwardLimitMm >= cgEnvelopeAftLimitMm) return null
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
        _state.value = transform(_state.value)
    }

    fun save() {
        val current = _state.value
        val domain = current.toDomainOrNull()
        if (domain == null) {
            // Surface it instead of doing nothing — see AirfieldEditViewModel.saveAirfieldInfo
            // for the same fix on the same silent-failure bug.
            _state.value = current.copy(saveAttempted = true)
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(isLoading = true)
            // Capture the returned id: for a brand-new profile (current.id == 0), that's the
            // only place the real auto-generated id ever becomes known.
            val savedId = repository.save(domain, current.id)
            _state.value = current.copy(id = savedId, isLoading = false, isSaved = true)
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
