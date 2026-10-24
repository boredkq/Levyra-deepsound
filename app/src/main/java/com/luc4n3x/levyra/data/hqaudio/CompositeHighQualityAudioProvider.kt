package com.luc4n3x.levyra.data.hqaudio

class CompositeHighQualityAudioProvider(
    private val providers: List<HighQualityAudioProvider>
) : HighQualityAudioProvider {
    override val id: String = "composite_hq"
    override val displayName: String = "Multi-Source HQ Audio"

    override suspend fun search(query: String, page: Int): ProviderSearchOutcome {
        val allCandidates = mutableListOf<AlternativeTrackCandidate>()
        for (provider in providers) {
            try {
                when (val result = provider.search(query, page)) {
                    is ProviderSearchOutcome.Found -> allCandidates.addAll(result.candidates)
                    else -> Unit
                }
            } catch (e: Exception) {
                // Ignore individual provider failure in search
            }
        }
        return if (allCandidates.isNotEmpty()) {
            ProviderSearchOutcome.Found(allCandidates)
        } else {
            ProviderSearchOutcome.Failed(ProviderFailure.NOT_FOUND)
        }
    }

    override suspend fun lookup(providerTrackId: String): ProviderLookupOutcome {
        for (provider in providers) {
            try {
                when (val outcome = provider.lookup(providerTrackId)) {
                    is ProviderLookupOutcome.Found -> return outcome
                    else -> Unit
                }
            } catch (e: Exception) {
                // Try next provider
            }
        }
        return ProviderLookupOutcome.Missing
    }

    override suspend fun resolveStream(candidate: AlternativeTrackCandidate): ProviderStreamOutcome {
        val targetProvider = providers.find { it.id == candidate.providerId }
            ?: providers.firstOrNull()
            ?: return ProviderStreamOutcome.Failed(ProviderFailure.NOT_FOUND)

        return try {
            targetProvider.resolveStream(candidate)
        } catch (e: Exception) {
            ProviderStreamOutcome.Failed(ProviderFailure.NETWORK)
        }
    }

    override fun health(): List<ProviderBackendHealth> = providers.flatMap { it.health() }
}
