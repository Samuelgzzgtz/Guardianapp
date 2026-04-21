package com.example.gab.util

import com.example.gab.data.model.Cuota
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun Cuota.calcularRecargo(): Double {
    if (estatus == "pagado" || fechaVencimiento == null || monto == null || monto == 0.0) return 0.0
    return try {
        val venc = LocalDate.parse(fechaVencimiento.take(10))
        val hoy = LocalDate.now()
        if (!hoy.isAfter(venc)) return 0.0
        val periodos = maxOf(ChronoUnit.DAYS.between(venc, hoy) / 30, 1L).toInt()
        (monto * 0.05 * periodos * 100).toLong() / 100.0
    } catch (_: Exception) { 0.0 }
}

fun Double.toMoneda(): String = "$${"%.2f".format(this)}"
