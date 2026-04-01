package top.yogiczy.mytv

import android.app.Application
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import top.yogiczy.mytv.ui.utils.SP
import java.security.SecureRandom
import javax.net.ssl.SSLContext

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        UnsafeTrustManager.enableUnsafeTrustManager()
        AppGlobal.cacheDir = applicationContext.cacheDir
        SP.init(applicationContext)

        // 配置 Coil 使用信任所有证书的 OkHttp 客户端，以支持 IPTV 台标的 HTTPS 加载
        val trustManager = UnsafeTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .build()
        )
    }
}
