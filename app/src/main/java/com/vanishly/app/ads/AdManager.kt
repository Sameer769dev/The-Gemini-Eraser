package com.vanishly.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    companion object {
        // Test Ad Unit IDs
        const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    init {
        loadInterstitialAd()
        loadRewardedAd()
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }
        })
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }
        })
    }

    /**
     * Shows Interstitial Ad if loaded. Invokes [onFinish] when closed.
     * Invokes [onAdFailed] if the ad is not ready or fails to show.
     */
    fun showInterstitial(activity: Activity, onFinish: () -> Unit, onAdFailed: () -> Unit) {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd() // pre-load next one
                    onFinish()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onAdFailed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            // Ad not ready, fallback to Paywall!
            loadInterstitialAd()
            onAdFailed()
        }
    }

    /**
     * Shows Rewarded Ad. Invokes [onReward] if the user completes the video,
     * and [onClosed] when the ad UI is dismissed completely.
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit, onAdFailed: () -> Unit) {
        if (rewardedAd != null) {
            var earnedReward = false
            rewardedAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                    if (earnedReward) {
                        onReward()
                    }
                    onClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    onAdFailed()
                }
            }
            rewardedAd?.show(activity) { rewardItem ->
                // User earned the reward.
                earnedReward = true
            }
        } else {
            // Ad not ready, fallback to Paywall!
            loadRewardedAd()
            onAdFailed()
        }
    }
}
