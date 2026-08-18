package com.example.dreamnet.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    // Real IDs
    private const val BANNER_ID = "ca-app-pub-4021098139898309/6986191650"
    private const val INTERSTITIAL_ID = "ca-app-pub-4021098139898309/1662667072"
    private const val REWARDED_ID = "ca-app-pub-4021098139898309/7545549143"

    // Test IDs (for development)
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    // Автоматическое переключение: true для debug, false для release
    var isTestMode = com.example.dreamnet.BuildConfig.DEBUG
        private set

    private var mInterstitialAd: InterstitialAd? = null
    private var mRewardedAd: RewardedAd? = null
    
    private var isInterstitialLoading = false
    private var isRewardedLoading = false
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            Log.d(TAG, "AdMob Initialized")
            loadInterstitial(context)
            loadRewarded(context)
        }
    }

    fun setTestMode(test: Boolean) {
        isTestMode = test
    }
    
    fun isInterstitialReady() = mInterstitialAd != null
    fun isRewardedReady() = mRewardedAd != null

    private fun getBannerId() = if (isTestMode) TEST_BANNER_ID else BANNER_ID
    private fun getInterstitialId() = if (isTestMode) TEST_INTERSTITIAL_ID else INTERSTITIAL_ID
    private fun getRewardedId() = if (isTestMode) TEST_REWARDED_ID else REWARDED_ID

    @Composable
    fun BannerAdView() {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = getBannerId()
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }

    fun loadInterstitial(context: Context) {
        if (isInterstitialLoading || mInterstitialAd != null) return
        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, getInterstitialId(), adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "Interstitial failed to load: ${adError.message}")
                mInterstitialAd = null
                isInterstitialLoading = false
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Interstitial loaded")
                mInterstitialAd = interstitialAd
                isInterstitialLoading = false
            }
        })
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial dismissed")
                    mInterstitialAd = null
                    loadInterstitial(activity)
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Interstitial failed to show: ${adError.message}")
                    mInterstitialAd = null
                    onDismissed()
                }
            }
            mInterstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Interstitial not ready")
            onDismissed()
        }
    }

    fun loadRewarded(context: Context) {
        if (isRewardedLoading || mRewardedAd != null) return
        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, getRewardedId(), adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "Rewarded failed to load: ${adError.message}")
                mRewardedAd = null
                isRewardedLoading = false
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d(TAG, "Rewarded loaded")
                mRewardedAd = rewardedAd
                isRewardedLoading = false
            }
        })
    }

    fun showRewarded(activity: Activity, onEarnedReward: () -> Unit) {
        if (mRewardedAd != null) {
            mRewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded dismissed")
                    mRewardedAd = null
                    loadRewarded(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Rewarded failed to show: ${adError.message}")
                    mRewardedAd = null
                }
            }
            mRewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onEarnedReward()
            }
        } else {
            Log.d(TAG, "Rewarded not ready")
        }
    }
}
