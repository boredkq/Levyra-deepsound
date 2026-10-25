package com.luc4n3x.levyra.data.yandex

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

internal object YandexResilientDns : Dns {
    private val HARDCODED_HOSTS: Map<String, List<String>> = mapOf(
        "oauth.yandex.ru" to listOf("87.250.251.227", "77.88.54.227"),
        "oauth.yandex.com" to listOf("87.250.251.227", "77.88.54.227"),
        "api.music.yandex.net" to listOf("87.250.250.246", "77.88.55.246"),
        "music.yandex.ru" to listOf("87.250.250.246", "77.88.55.246"),
        "ya.ru" to listOf("87.250.250.242", "77.88.55.242"),
        "yandex.ru" to listOf("87.250.250.242", "77.88.55.242"),
        "yandex.com" to listOf("87.250.250.242", "77.88.55.242")
    )

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. Try system DNS first
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (_: UnknownHostException) {
            // System DNS failed (Private DNS block, AdGuard, restricted network)
        }

        // 2. Try .com equivalent if .ru failed
        if (hostname.endsWith(".ru")) {
            val comHost = hostname.removeSuffix(".ru") + ".com"
            try {
                val addresses = Dns.SYSTEM.lookup(comHost)
                if (addresses.isNotEmpty()) return addresses
            } catch (_: UnknownHostException) {
                // Continue to fallback
            }
        }

        // 3. Fallback to hardcoded known official Yandex IP addresses
        val ips = HARDCODED_HOSTS[hostname]
            ?: if (hostname.endsWith(".ru")) HARDCODED_HOSTS[hostname.removeSuffix(".ru") + ".com"] else null

        if (!ips.isNullOrEmpty()) {
            val resolved = ips.mapNotNull { ip ->
                runCatching {
                    InetAddress.getByAddress(hostname, InetAddress.getByName(ip).address)
                }.getOrNull()
            }
            if (resolved.isNotEmpty()) return resolved
        }

        throw UnknownHostException("Unable to resolve host \"$hostname\"")
    }
}
