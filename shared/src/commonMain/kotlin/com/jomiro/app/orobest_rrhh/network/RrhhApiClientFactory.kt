package com.jomiro.app.orobest_rrhh.network

/** Creates session clients with the server and token received from the login flow. */
class RrhhApiClientFactory {
    fun create(baseUrl: String, bearerToken: String): RrhhApiClient =
        RrhhApiClient(baseUrl = baseUrl, bearerToken = bearerToken)
}
