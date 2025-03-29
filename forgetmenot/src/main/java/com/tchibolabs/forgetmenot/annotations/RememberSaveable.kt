package com.tchibolabs.forgetmenot.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RememberSaveable(val injector: String = "")