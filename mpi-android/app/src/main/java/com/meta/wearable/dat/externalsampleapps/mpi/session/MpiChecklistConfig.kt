package com.meta.wearable.dat.externalsampleapps.mpi.session

object MpiChecklistConfig {
    private val tireTreadMeasurement =
        MpiMeasurementMetadata(
            valueType = MpiValueType.NUMBER,
            unit = "/32",
            possibleUnits = listOf("/32"),
            thresholds =
                MpiMeasurementThresholds(
                    greenMin = 6.0,
                    yellowMin = 4.0,
                    yellowMax = 5.0,
                    redMax = 3.0,
                ),
        )

    private val tirePressureMeasurement =
        MpiMeasurementMetadata(
            valueType = MpiValueType.NUMBER,
            unit = "PSI",
            possibleUnits = listOf("PSI"),
        )

    private val brakePadMeasurement =
        MpiMeasurementMetadata(
            valueType = MpiValueType.NUMBER,
            unit = "mm",
            possibleUnits = listOf("mm"),
            thresholds =
                MpiMeasurementThresholds(
                    greenMin = 6.0,
                    yellowMin = 4.0,
                    yellowMax = 5.0,
                    redMax = 3.0,
                ),
        )

    private val batteryVoltageMeasurement =
        MpiMeasurementMetadata(
            valueType = MpiValueType.NUMBER,
            unit = "V",
            possibleUnits = listOf("V"),
            thresholds =
                MpiMeasurementThresholds(
                    greenMin = 12.4,
                    yellowMin = 12.1,
                    yellowMax = 12.3,
                    redBelow = 12.1,
                ),
        )

    private val fluidLevelMeasurement =
        MpiMeasurementMetadata(
            valueType = MpiValueType.SELECT,
            selectOptions = listOf("low", "ok", "high", "unknown"),
        )

    private val fluidConditionMeasurement =
        MpiMeasurementMetadata(
            valueType = MpiValueType.SELECT,
            selectOptions = listOf("ok", "dirty", "contaminated", "unknown"),
        )

    val DEFAULT_SECTIONS: List<MpiChecklistSectionConfig> =
        listOf(
            section(
                id = "exterior_lights_safety",
                title = "Exterior / Lights / Safety",
                collapsedByDefault = false,
                items =
                    listOf(
                        item("warning_lights", "Warning lights", "warning light", "dash warning"),
                        item("headlights", "Headlights", "headlight", "low beam"),
                        item("high_beam_headlights", "High beam headlights", "high beam", "brights"),
                        item("parking_lamps", "Parking lamps", "parking lamp", "parking light"),
                        item("brake_reverse_lights", "Brake and reverse lights", "brake light", "reverse light"),
                        item("license_plate_lights", "License plate lights", "plate light"),
                        item("turn_signals", "Turn signals", "turn signal", "blinker"),
                        item("horn", "Horn"),
                        item("wiper_blades", "Wiper blades", "wiper", "wipers", "blade"),
                        item("windshield_washer_spray", "Windshield washer spray", "washer spray"),
                        item("windshield_condition", "Windshield condition", "windshield", "glass"),
                        item("exterior_body_damage", "Exterior body damage", "body damage", "dent", "scratch"),
                        item("mirrors", "Mirrors", "mirror"),
                        item("door_operation", "Door operation", "door"),
                    ),
            ),
            section(
                id = "interior_cabin",
                title = "Interior / Cabin",
                items =
                    listOf(
                        item("interior_lights", "Interior lights", "dome light", "map light"),
                        item("seat_belts", "Seat belts", "seat belt", "seatbelt"),
                        item("hvac_operation", "HVAC operation", "hvac", "ac", "air conditioning", "heater"),
                        item("cabin_air_filter", "Cabin air filter", "cabin filter"),
                        item("dashboard_indicators", "Dashboard indicators", "dashboard", "dash indicators"),
                        item("parking_brake", "Parking brake", "e brake", "emergency brake"),
                        item("clutch_operation", "Clutch operation, if applicable", "clutch"),
                        item("brake_pedal_feel", "Brake pedal feel", "brake pedal"),
                        item("steering_wheel_controls", "Steering wheel controls", "steering controls"),
                    ),
            ),
            section(
                id = "under_hood_fluids",
                title = "Under Hood / Fluids",
                items =
                    listOf(
                        item("engine_oil_level", "Engine oil level", "oil level", measurement = fluidLevelMeasurement),
                        item(
                            "engine_oil_condition",
                            "Engine oil condition",
                            "oil condition",
                            "dirty oil",
                            measurement = fluidConditionMeasurement,
                        ),
                        item("coolant_antifreeze", "Coolant / antifreeze", "coolant", "antifreeze", measurement = fluidLevelMeasurement),
                        item("brake_fluid", "Brake fluid", "brake fluid", measurement = fluidConditionMeasurement),
                        item("power_steering_fluid", "Power steering fluid", "power steering", measurement = fluidLevelMeasurement),
                        item("transmission_fluid", "Transmission fluid", "transmission fluid", measurement = fluidConditionMeasurement),
                        item("windshield_washer_fluid", "Windshield washer fluid", "washer fluid", measurement = fluidLevelMeasurement),
                        item("engine_air_filter", "Engine air filter", "air filter"),
                        item("battery_condition", "Battery condition", "battery", "battery voltage", measurement = batteryVoltageMeasurement),
                        item("battery_terminals_cables", "Battery terminals / cables", "battery terminal", "battery cable"),
                        item("belts", "Belts", "belt", "serpentine"),
                        item("radiator_hoses", "Radiator hoses", "radiator hose", "hose"),
                        item("visible_leaks", "Visible leaks", "visible leak", "leak"),
                        item("fuel_system_visual_check", "Fuel system visual check", "fuel system", "fuel leak"),
                    ),
            ),
            section(
                id = "tires_wheels_brakes",
                title = "Tires / Wheels / Brakes",
                collapsedByDefault = false,
                items =
                    listOf(
                        item("lf_tire_tread", "Left front tire tread depth", "lf tire tread", "left front tread", measurement = tireTreadMeasurement),
                        item("rf_tire_tread", "Right front tire tread depth", "rf tire tread", "right front tread", measurement = tireTreadMeasurement),
                        item("lr_tire_tread", "Left rear tire tread depth", "lr tire tread", "left rear tread", measurement = tireTreadMeasurement),
                        item("rr_tire_tread", "Right rear tire tread depth", "rr tire tread", "right rear tread", measurement = tireTreadMeasurement),
                        item("lf_tire_pressure", "Left front tire pressure", "lf tire pressure", "left front pressure", measurement = tirePressureMeasurement),
                        item("rf_tire_pressure", "Right front tire pressure", "rf tire pressure", "right front pressure", measurement = tirePressureMeasurement),
                        item("lr_tire_pressure", "Left rear tire pressure", "lr tire pressure", "left rear pressure", measurement = tirePressureMeasurement),
                        item("rr_tire_pressure", "Right rear tire pressure", "rr tire pressure", "right rear pressure", measurement = tirePressureMeasurement),
                        item("lf_brake_pad", "Left front brake pad thickness", "lf brake pad", "left front brake pad", measurement = brakePadMeasurement),
                        item("rf_brake_pad", "Right front brake pad thickness", "rf brake pad", "right front brake pad", measurement = brakePadMeasurement),
                        item("lr_brake_pad", "Left rear brake pad thickness", "lr brake pad", "left rear brake pad", measurement = brakePadMeasurement),
                        item("rr_brake_pad", "Right rear brake pad thickness", "rr brake pad", "right rear brake pad", measurement = brakePadMeasurement),
                        item("tire_wear_pattern", "Tire wear pattern", "tire wear", "wear pattern"),
                        item("sidewall_condition", "Sidewall condition", "sidewall"),
                        item("wheel_damage", "Wheel damage", "wheel", "rim"),
                        item("brake_rotor_condition", "Brake rotor condition", "rotor", "brake rotor"),
                        item("brake_lines_hoses", "Brake lines / hoses", "brake line", "brake hose"),
                    ),
            ),
            section(
                id = "under_vehicle_suspension",
                title = "Under Vehicle / Suspension",
                items =
                    listOf(
                        item("engine_oil_leak", "Engine oil leak", "oil leak"),
                        item("transmission_fluid_leak", "Transmission fluid leak", "transmission leak"),
                        item("coolant_leak", "Coolant leak", "coolant leak"),
                        item("exhaust_system", "Exhaust system", "exhaust"),
                        item("shocks_struts", "Shocks / struts", "shock", "strut"),
                        item("suspension_components", "Suspension components", "suspension"),
                        item("tie_rod_ends", "Tie rod ends", "tie rod"),
                        item("ball_joints", "Ball joints", "ball joint"),
                        item("steering_rack_gear", "Steering rack / gear", "steering rack", "steering gear"),
                        item("cv_boots", "CV boots", "cv boot"),
                        item("drive_shaft_boots", "Drive shaft boots", "drive shaft boot"),
                        item("underbody_damage", "Underbody damage", "underbody"),
                        item("parking_brake_cable", "Parking brake cable", "brake cable"),
                        item("fasteners_missing_shields", "Fasteners / missing shields", "fastener", "missing shield"),
                    ),
            ),
            section(
                id = "road_test_symptoms",
                title = "Road Test / Symptoms",
                items =
                    listOf(
                        item("pulls_pulsation", "Pulls / pulsation", "pull", "pulsation"),
                        item("balance_noise", "Balance / noise", "balance", "vibration"),
                        item("brake_noise", "Brake noise", "brake noise"),
                        item("steering_noise", "Steering noise", "steering noise"),
                        item("engine_noise", "Engine noise", "engine noise"),
                        item("transmission_shift_quality", "Transmission shift quality", "shift quality", "shifting"),
                        item("warning_lights_during_operation", "Warning lights during operation", "warning during operation"),
                        item("general_drivability_concern", "General drivability concern", "drivability"),
                    ),
            ),
            section(
                id = "needs_technician_review",
                title = "Needs Technician Review",
                collapsedByDefault = false,
                items =
                    listOf(
                        item("unclear_visual_evidence", "Unclear visual evidence", "unclear", "not clear"),
                        item("customer_concern_not_verified", "Customer concern not verified", "not verified"),
                        item("additional_diagnostic_time_recommended", "Additional diagnostic time recommended", "diagnostic time"),
                        item("manual_confirmation_required", "Manual confirmation required", "manual confirmation"),
                    ),
            ),
        )

    val ITEMS_BY_ID: Map<String, MpiChecklistItemConfig> =
        DEFAULT_SECTIONS.flatMap { it.items }.associateBy { it.id }

    private fun section(
        id: String,
        title: String,
        description: String? = null,
        collapsedByDefault: Boolean = true,
        items: List<MpiChecklistItemConfig>,
    ): MpiChecklistSectionConfig =
        MpiChecklistSectionConfig(
            id = id,
            title = title,
            description = description,
            collapsedByDefault = collapsedByDefault,
            items = items,
        )

    private fun item(
        id: String,
        label: String,
        vararg aliases: String,
        measurement: MpiMeasurementMetadata = MpiMeasurementMetadata(),
    ): MpiChecklistItemConfig =
        MpiChecklistItemConfig(
            id = id,
            label = label,
            aliases = aliases.toList(),
            measurement = measurement,
        )
}
