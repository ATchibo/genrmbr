package com.tchibolabs.genrmbr.rememberedsaveable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RememberSaveable(val injector: String = "")