package com.meta.wearable.dat.core.types

sealed interface RegistrationState {
    class Unavailable : RegistrationState
    class Registering : RegistrationState
    class Registered : RegistrationState
    class Unregistering : RegistrationState
}
