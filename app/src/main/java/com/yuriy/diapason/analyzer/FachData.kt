package com.yuriy.diapason.analyzer

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.yuriy.diapason.R


/**
 * Complete Fach definition including acoustic parameters AND musical context
 * (famous roles, example singers). The latter powers the Voice Types screen.
 */
data class FachDefinition(
    @StringRes val nameRes: Int,
    @StringRes val categoryRes: Int,           // broad grouping label
    val rangeMinHz: Float,
    val rangeMaxHz: Float,
    val tessituraMinHz: Float,
    val tessituraMaxHz: Float,
    val passaggioHz: Float,
    @StringRes val descriptionRes: Int,        // one-line character description
    @ArrayRes val famousRolesRes: Int,         // opera / classical roles
    @ArrayRes val exampleSingersRes: Int
)

data class VoiceProfile(
    val absoluteMinHz: Float,
    val absoluteMaxHz: Float,
    val tessituraLowHz: Float,
    val tessituraHighHz: Float,
    val estimatedPassaggioHz: Float,
    val sampleCount: Int,
    val durationSeconds: Float
)

data class FachMatch(
    val fach: FachDefinition,
    val score: Int,
    val maxScore: Int = 14,
    val scoreBreakdown: List<String>
)

// ─────────────────────────────────────────────────────────────────────────────
// Full Fach table
// ─────────────────────────────────────────────────────────────────────────────

val ALL_FACH: List<FachDefinition> = listOf(

    FachDefinition(
        nameRes = R.string.fach_name_coloratura_soprano,
        categoryRes = R.string.fach_category_soprano,
        rangeMinHz = 262f, rangeMaxHz = 2093f,
        tessituraMinHz = 440f, tessituraMaxHz = 1319f,
        passaggioHz = 659f,
        descriptionRes = R.string.fach_description_coloratura_soprano,
        famousRolesRes = R.array.fach_famous_roles_coloratura_soprano,
        exampleSingersRes = R.array.fach_example_singers_coloratura_soprano
    ),

    FachDefinition(
        nameRes = R.string.fach_name_lyric_coloratura_soprano,
        categoryRes = R.string.fach_category_soprano,
        rangeMinHz = 262f, rangeMaxHz = 1397f,
        tessituraMinHz = 349f, tessituraMaxHz = 1047f,
        passaggioHz = 523f,
        descriptionRes = R.string.fach_description_lyric_coloratura_soprano,
        famousRolesRes = R.array.fach_famous_roles_lyric_coloratura_soprano,
        exampleSingersRes = R.array.fach_example_singers_lyric_coloratura_soprano
    ),

    FachDefinition(
        nameRes = R.string.fach_name_lyric_soprano,
        categoryRes = R.string.fach_category_soprano,
        rangeMinHz = 247f, rangeMaxHz = 1047f,
        tessituraMinHz = 294f, tessituraMaxHz = 880f,
        passaggioHz = 494f,
        descriptionRes = R.string.fach_description_lyric_soprano,
        famousRolesRes = R.array.fach_famous_roles_lyric_soprano,
        exampleSingersRes = R.array.fach_example_singers_lyric_soprano
    ),

    FachDefinition(
        nameRes = R.string.fach_name_spinto_soprano,
        categoryRes = R.string.fach_category_soprano,
        rangeMinHz = 233f, rangeMaxHz = 988f,
        tessituraMinHz = 277f, tessituraMaxHz = 784f,
        passaggioHz = 466f,
        descriptionRes = R.string.fach_description_spinto_soprano,
        famousRolesRes = R.array.fach_famous_roles_spinto_soprano,
        exampleSingersRes = R.array.fach_example_singers_spinto_soprano
    ),

    FachDefinition(
        nameRes = R.string.fach_name_dramatic_soprano,
        categoryRes = R.string.fach_category_soprano,
        rangeMinHz = 220f, rangeMaxHz = 880f,
        tessituraMinHz = 262f, tessituraMaxHz = 698f,
        passaggioHz = 440f,
        descriptionRes = R.string.fach_description_dramatic_soprano,
        famousRolesRes = R.array.fach_famous_roles_dramatic_soprano,
        exampleSingersRes = R.array.fach_example_singers_dramatic_soprano
    ),

    FachDefinition(
        nameRes = R.string.fach_name_lyric_mezzo_soprano,
        categoryRes = R.string.fach_category_mezzo_soprano,
        rangeMinHz = 196f, rangeMaxHz = 988f,
        tessituraMinHz = 247f, tessituraMaxHz = 740f,
        passaggioHz = 392f,
        descriptionRes = R.string.fach_description_lyric_mezzo_soprano,
        famousRolesRes = R.array.fach_famous_roles_lyric_mezzo_soprano,
        exampleSingersRes = R.array.fach_example_singers_lyric_mezzo_soprano
    ),

    FachDefinition(
        nameRes = R.string.fach_name_dramatic_mezzo_soprano,
        categoryRes = R.string.fach_category_mezzo_soprano,
        rangeMinHz = 175f, rangeMaxHz = 784f,
        tessituraMinHz = 220f, tessituraMaxHz = 622f,
        passaggioHz = 370f,
        descriptionRes = R.string.fach_description_dramatic_mezzo_soprano,
        famousRolesRes = R.array.fach_famous_roles_dramatic_mezzo_soprano,
        exampleSingersRes = R.array.fach_example_singers_dramatic_mezzo_soprano
    ),

    FachDefinition(
        nameRes = R.string.fach_name_contralto,
        categoryRes = R.string.fach_category_contralto,
        rangeMinHz = 165f, rangeMaxHz = 698f,
        tessituraMinHz = 196f, tessituraMaxHz = 523f,
        passaggioHz = 330f,
        descriptionRes = R.string.fach_description_contralto,
        famousRolesRes = R.array.fach_famous_roles_contralto,
        exampleSingersRes = R.array.fach_example_singers_contralto
    ),

    FachDefinition(
        nameRes = R.string.fach_name_countertenor,
        categoryRes = R.string.fach_category_countertenor,
        rangeMinHz = 165f, rangeMaxHz = 880f,
        tessituraMinHz = 220f, tessituraMaxHz = 659f,
        passaggioHz = 330f,
        descriptionRes = R.string.fach_description_countertenor,
        famousRolesRes = R.array.fach_famous_roles_countertenor,
        exampleSingersRes = R.array.fach_example_singers_countertenor
    ),

    FachDefinition(
        nameRes = R.string.fach_name_lyric_tenor,
        categoryRes = R.string.fach_category_tenor,
        rangeMinHz = 130f, rangeMaxHz = 523f,
        tessituraMinHz = 196f, tessituraMaxHz = 440f,
        passaggioHz = 311f,
        descriptionRes = R.string.fach_description_lyric_tenor,
        famousRolesRes = R.array.fach_famous_roles_lyric_tenor,
        exampleSingersRes = R.array.fach_example_singers_lyric_tenor
    ),

    FachDefinition(
        nameRes = R.string.fach_name_spinto_tenor,
        categoryRes = R.string.fach_category_tenor,
        rangeMinHz = 123f, rangeMaxHz = 494f,
        tessituraMinHz = 175f, tessituraMaxHz = 415f,
        passaggioHz = 294f,
        descriptionRes = R.string.fach_description_spinto_tenor,
        famousRolesRes = R.array.fach_famous_roles_spinto_tenor,
        exampleSingersRes = R.array.fach_example_singers_spinto_tenor
    ),

    FachDefinition(
        nameRes = R.string.fach_name_dramatic_tenor_heldentenor,
        categoryRes = R.string.fach_category_tenor,
        rangeMinHz = 110f, rangeMaxHz = 466f,
        tessituraMinHz = 165f, tessituraMaxHz = 392f,
        passaggioHz = 277f,
        descriptionRes = R.string.fach_description_dramatic_tenor_heldentenor,
        famousRolesRes = R.array.fach_famous_roles_dramatic_tenor_heldentenor,
        exampleSingersRes = R.array.fach_example_singers_dramatic_tenor_heldentenor
    ),

    FachDefinition(
        nameRes = R.string.fach_name_lyric_baritone,
        categoryRes = R.string.fach_category_baritone,
        rangeMinHz = 110f, rangeMaxHz = 392f,
        tessituraMinHz = 147f, tessituraMaxHz = 330f,
        passaggioHz = 220f,
        descriptionRes = R.string.fach_description_lyric_baritone,
        famousRolesRes = R.array.fach_famous_roles_lyric_baritone,
        exampleSingersRes = R.array.fach_example_singers_lyric_baritone
    ),

    FachDefinition(
        nameRes = R.string.fach_name_kavalierbariton,
        categoryRes = R.string.fach_category_baritone,
        rangeMinHz = 98f, rangeMaxHz = 370f,
        tessituraMinHz = 138f, tessituraMaxHz = 311f,
        passaggioHz = 207f,
        descriptionRes = R.string.fach_description_kavalierbariton,
        famousRolesRes = R.array.fach_famous_roles_kavalierbariton,
        exampleSingersRes = R.array.fach_example_singers_kavalierbariton
    ),

    FachDefinition(
        nameRes = R.string.fach_name_dramatic_baritone,
        categoryRes = R.string.fach_category_baritone,
        rangeMinHz = 87f, rangeMaxHz = 349f,
        tessituraMinHz = 123f, tessituraMaxHz = 294f,
        passaggioHz = 196f,
        descriptionRes = R.string.fach_description_dramatic_baritone,
        famousRolesRes = R.array.fach_famous_roles_dramatic_baritone,
        exampleSingersRes = R.array.fach_example_singers_dramatic_baritone
    ),

    FachDefinition(
        nameRes = R.string.fach_name_bass_baritone,
        categoryRes = R.string.fach_category_bass,
        rangeMinHz = 82f, rangeMaxHz = 330f,
        tessituraMinHz = 110f, tessituraMaxHz = 277f,
        passaggioHz = 185f,
        descriptionRes = R.string.fach_description_bass_baritone,
        famousRolesRes = R.array.fach_famous_roles_bass_baritone,
        exampleSingersRes = R.array.fach_example_singers_bass_baritone
    ),

    FachDefinition(
        nameRes = R.string.fach_name_basso_cantante,
        categoryRes = R.string.fach_category_bass,
        rangeMinHz = 73f, rangeMaxHz = 330f,
        tessituraMinHz = 98f, tessituraMaxHz = 262f,
        passaggioHz = 175f,
        descriptionRes = R.string.fach_description_basso_cantante,
        famousRolesRes = R.array.fach_famous_roles_basso_cantante,
        exampleSingersRes = R.array.fach_example_singers_basso_cantante
    ),

    FachDefinition(
        nameRes = R.string.fach_name_basso_profundo,
        categoryRes = R.string.fach_category_bass,
        rangeMinHz = 65f, rangeMaxHz = 294f,
        tessituraMinHz = 82f, tessituraMaxHz = 220f,
        passaggioHz = 155f,
        descriptionRes = R.string.fach_description_basso_profundo,
        famousRolesRes = R.array.fach_famous_roles_basso_profundo,
        exampleSingersRes = R.array.fach_example_singers_basso_profundo
    ),

    FachDefinition(
        nameRes = R.string.fach_name_contrabass_oktavist,
        categoryRes = R.string.fach_category_bass,
        rangeMinHz = 43f, rangeMaxHz = 220f,
        tessituraMinHz = 65f, tessituraMaxHz = 165f,
        passaggioHz = 130f,
        descriptionRes = R.string.fach_description_contrabass_oktavist,
        famousRolesRes = R.array.fach_famous_roles_contrabass_oktavist,
        exampleSingersRes = R.array.fach_example_singers_contrabass_oktavist
    )

)
