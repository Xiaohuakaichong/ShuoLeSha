package com.example.shuolesa.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography=Typography(
    headlineLarge=TextStyle(fontSize=32.sp,lineHeight=40.sp,fontWeight=FontWeight.SemiBold),
    headlineMedium=TextStyle(fontSize=26.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontSize=18.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold),
    titleSmall=TextStyle(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=26.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=22.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
    labelMedium=TextStyle(fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium),
)
