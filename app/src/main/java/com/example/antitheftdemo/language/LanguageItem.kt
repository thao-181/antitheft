package com.example.antitheftdemo.language

import com.example.antitheftdemo.R

data class LanguageItem(
    val title: String,
    val icon: Int,
    val code: String = requireNotNull(LanguageCodes.code(title))
)

var listItem = listOf(
    LanguageItem(
        title = "English",
        icon = R.drawable.english
    ),
    LanguageItem(
        title = "Hindi",
        icon = R.drawable.hindi
    ),
    LanguageItem(
        title = "Spanish",
        icon = R.drawable.spanish
    ),
    LanguageItem(
        title = "French",
        icon = R.drawable.french
    ),
    LanguageItem(
        title = "German",
        icon = R.drawable.german
    ),
    LanguageItem(
        title = "Indonesian",
        icon = R.drawable.indonesian
    ),
    LanguageItem(
        title = "Portuguese",
        icon = R.drawable.portuguese
    )

)

