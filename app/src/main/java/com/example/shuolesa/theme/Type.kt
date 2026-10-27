package com.example.shuolesa.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** v5.0: fewer, clearer steps. Headlines are bold, body text keeps the roomy Chinese line height. */
val Typography=Typography(
    displayMedium=TextStyle(fontSize=56.sp,lineHeight=64.sp,fontWeight=FontWeight.Light,fontFeatureSettings="tnum"),
    displaySmall=TextStyle(fontSize=36.sp,lineHeight=44.sp,fontWeight=FontWeight.Bold),
    headlineLarge=TextStyle(fontSize=32.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold),
    headlineMedium=TextStyle(fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold),
    headlineSmall=TextStyle(fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold),
    titleLarge=TextStyle(fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontSize=17.sp,lineHeight=25.sp,fontWeight=FontWeight.SemiBold),
    titleSmall=TextStyle(fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=26.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=22.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
    labelMedium=TextStyle(fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium),
    labelSmall=TextStyle(fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium),
)
