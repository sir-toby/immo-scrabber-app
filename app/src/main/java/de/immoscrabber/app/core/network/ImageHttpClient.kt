package de.immoscrabber.app.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * OkHttp-Client für Anbieterbilder (Coil), ohne Auth-Interceptor: Tokens gehen nie an fremde
 * Server (Entscheidung #10). Ein Verbindungsfehler wird wie beim API-Client einmal still
 * wiederholt (#56), sonst bliebe nach einem einmaligen Fehler dauerhaft der Platzhalter stehen.
 * Anders als dort wird der Body nicht gepuffert, Coil streamt ihn.
 */
fun createImageHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .addInterceptor(RetryIdempotentReadInterceptor(bufferBody = false))
    .build()
