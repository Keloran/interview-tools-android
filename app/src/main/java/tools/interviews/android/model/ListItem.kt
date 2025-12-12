package tools.interviews.android.model

import java.time.LocalDate

data class ListItem(
    val id: Long,
    val name: String,
    val companyName: String,
    val date: LocalDate,
    val label1: String,
    val label2: String
)