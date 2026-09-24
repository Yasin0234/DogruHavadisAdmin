package com.korkutsoftware.doruhavadisadmin.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.print.PdfAdapterExporter
import android.print.PrintAttributes
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.korkutsoftware.doruhavadisadmin.models.Columnist
import com.korkutsoftware.doruhavadisadmin.models.News
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NewspaperPdfGenerator(private val context: Context) {

    private val db = FirebaseFirestore.getInstance("dogruhavadis")

    data class AdItem(
        val title: String = "REKLAM ALANI",
        val description: String = "Doğru Havadis Gazetesi'nde reklamınızın yer almasını isterseniz bizimle iletişime geçin.",
        val phone: String = "0505 000 00 00",
        val imageUri: Uri? = null,
        val imageBase64: String? = null
    )

    data class PdfOptions(
        val startDateMillis: Long,
        val endDateMillis: Long,
        val pageSize: String = "A3", // "A3" or "A4"
        val includeColumnists: Boolean = true,
        val selectedNewsIds: List<String>? = null,
        val mainHeadlineNewsId: String? = null,
        val coverNewsIds: List<String>? = null,
        val ads: List<AdItem> = emptyList()
    )

    data class ProcessedColumnist(
        val columnist: Columnist,
        val writerImageBase64: String?,
        val contentImageBase64: String?
    )

    interface PdfCallback {
        fun onSuccess(pdfFile: File, htmlContent: String)
        fun onError(exception: Exception)
    }

    suspend fun fetchNewsForDateRange(startDateMillis: Long, endDateMillis: Long): List<News> = withContext(Dispatchers.IO) {
        val newsList = mutableListOf<News>()
        try {
            val task = db.collection("news")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()

            val snapshot: QuerySnapshot = Tasks.await(task)

            for (doc in snapshot.documents) {
                val news = doc.toObject(News::class.java) ?: continue
                val timeMillis = parseTimestamp(news.timestamp)
                if (timeMillis in startDateMillis..endDateMillis) {
                    newsList.add(news.copy(id = doc.id))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext newsList
    }

    suspend fun fetchColumnistsForDateRange(startDateMillis: Long, endDateMillis: Long): List<Columnist> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Columnist>()
        try {
            val task = db.collection("columnists")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()

            val snapshot: QuerySnapshot = Tasks.await(task)

            for (doc in snapshot.documents) {
                val item = doc.toObject(Columnist::class.java) ?: continue
                val timeMillis = parseTimestamp(item.timestamp)
                if (timeMillis in startDateMillis..endDateMillis || timeMillis == 0L) {
                    list.add(item.copy(id = doc.id))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    private fun parseTimestamp(timestamp: Any?): Long {
        return when (timestamp) {
            is Long -> timestamp
            is Timestamp -> timestamp.toDate().time
            is Double -> timestamp.toLong()
            is Map<*, *> -> {
                val seconds = (timestamp["seconds"] as? Number)?.toLong() ?: 0L
                seconds * 1000
            }
            else -> 0L
        }
    }

    suspend fun generatePdf(
        options: PdfOptions,
        callback: PdfCallback
    ) = withContext(Dispatchers.Main) {
        try {
            val allNewsList = fetchNewsForDateRange(options.startDateMillis, options.endDateMillis)
            
            val newsList = if (!options.selectedNewsIds.isNullOrEmpty()) {
                allNewsList.filter { options.selectedNewsIds.contains(it.id) }
            } else {
                allNewsList
            }

            val columnistsList = if (options.includeColumnists) {
                fetchColumnistsForDateRange(options.startDateMillis, options.endDateMillis)
            } else {
                emptyList()
            }
            
            if (newsList.isEmpty() && columnistsList.isEmpty()) {
                callback.onError(Exception("Seçilen tarih aralığında gösterilecek haber veya köşe yazısı bulunamadı."))
                return@withContext
            }

            // Convert news images to Base64
            val processedNews = withContext(Dispatchers.IO) {
                newsList.map { news ->
                    val base64Images = news.mediaUrls.mapNotNull { url ->
                        downloadImageAsBase64(url)
                    }
                    news to base64Images
                }
            }

            // Convert columnists images to Base64
            val processedColumnists = withContext(Dispatchers.IO) {
                columnistsList.map { item ->
                    val writerImgBase64 = if (item.writerImageUrl.isNotEmpty()) downloadImageAsBase64(item.writerImageUrl) else null
                    val contentImgBase64 = if (item.contentImageUrl.isNotEmpty()) downloadImageAsBase64(item.contentImageUrl) else null
                    ProcessedColumnist(item, writerImgBase64, contentImgBase64)
                }
            }

            // Convert ad images to Base64
            val processedAds = withContext(Dispatchers.IO) {
                options.ads.map { ad ->
                    val base64Img = if (ad.imageUri != null) {
                        convertUriToBase64(ad.imageUri)
                    } else ad.imageBase64
                    ad.copy(imageBase64 = base64Img)
                }
            }

            val finalOptions = options.copy(ads = processedAds)

            val htmlContent = buildNewspaperHtml(processedNews, processedColumnists, finalOptions)
            renderHtmlToPdf(htmlContent, options.pageSize, callback)

        } catch (e: Exception) {
            callback.onError(e)
        }
    }

    private fun downloadImageAsBase64(imageUrl: String): String? {
        if (imageUrl.isEmpty()) return null
        return try {
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .submit(400, 300)
                .get()

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val byteArray = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun convertUriToBase64(uri: Uri): String? {
        return try {
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(uri)
                .submit(600, 400)
                .get()

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildAdHtml(ad: AdItem): String {
        val adImgTag = if (!ad.imageBase64.isNullOrEmpty()) {
            "<img class='ad-img' src='${ad.imageBase64}' alt='Reklam' />"
        } else ""
        return """
            <div class="ad-box">
                <div class="ad-header">${ad.title}</div>
                $adImgTag
                <p class="ad-desc">${ad.description}</p>
                <div class="ad-phone">İletişim: ${ad.phone}</div>
            </div>
        """.trimIndent()
    }

    private fun buildArticlesHtml(articles: List<Pair<News, List<String>>>, locale: Locale): String {
        val builder = StringBuilder()
        for ((news, images) in articles) {
            val imgTag = if (images.isNotEmpty()) {
                "<img class='article-img' src='${images[0]}' alt='${news.title}' />"
            } else ""

            val locationStr = if (news.province.isNotEmpty()) "${news.province.uppercase(locale)} ${if (news.district.isNotEmpty()) "- " + news.district.uppercase(locale) else ""}" else ""

            builder.append("""
                <article class="article-card">
                    ${if (locationStr.isNotEmpty()) "<div class='article-location'>$locationStr</div>" else ""}
                    <h2 class="article-title">${news.title}</h2>
                    $imgTag
                    <div class="article-text">${news.description.replace("\n", "<br/>")}</div>
                </article>
            """.trimIndent())
        }
        return builder.toString()
    }

    private fun buildNewspaperHtml(
        newsWithImages: List<Pair<News, List<String>>>,
        columnists: List<ProcessedColumnist>,
        options: PdfOptions
    ): String {
        val locale = Locale.forLanguageTag("tr-TR")
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", locale)
        val startDateStr = dateFormat.format(Date(options.startDateMillis))
        val endDateStr = dateFormat.format(Date(options.endDateMillis))
        val issueDateStr = "$startDateStr - $endDateStr"

        // Main Headline Selection
        val headlineItem = if (!options.mainHeadlineNewsId.isNullOrEmpty()) {
            newsWithImages.firstOrNull { it.first.id == options.mainHeadlineNewsId }
        } else {
            newsWithImages.firstOrNull { it.first.isHeadline } ?: newsWithImages.firstOrNull()
        }

        val otherItems = newsWithImages.filter { it != headlineItem }

        // Cover / Main Page Teasers Selection
        val coverItems = if (!options.coverNewsIds.isNullOrEmpty()) {
            val selectedCover = newsWithImages.filter { options.coverNewsIds.contains(it.first.id) && it != headlineItem }
            if (selectedCover.isNotEmpty()) selectedCover else otherItems.take(4)
        } else {
            otherItems.take(4)
        }

        val isA3 = options.pageSize.equals("A3", ignoreCase = true)
        val pageSizeCss = if (isA3) "A3 portrait" else "A4 portrait"
        val columnCountCss = if (isA3) "3" else "2"

        // PAGE 1: FRONT COVER (KAPAK SAYFASI)
        val surmansetHtml = if (otherItems.isNotEmpty()) {
            val teaserNews = otherItems.take(2)
            val teasersText = teaserNews.joinToString(" • ") { "FLASH: " + it.first.title.uppercase(locale) }
            """
            <div class="surmanset-banner">
                <span class="surmanset-tag">SÜRMANŞET</span>
                <span class="surmanset-text">$teasersText</span>
            </div>
            """.trimIndent()
        } else ""

        val headlineCoverHtml = if (headlineItem != null) {
            val (news, images) = headlineItem
            val imgTag = if (images.isNotEmpty()) {
                "<img class='headline-img' src='${images[0]}' alt='${news.title}' />"
            } else ""

            val locationStr = if (news.province.isNotEmpty()) "${news.province.uppercase(locale)} / ${news.district.uppercase(locale)}" else ""
            val excerptText = news.description.take(380) + if (news.description.length > 380) "..." else ""

            """
            <div class="headline-container">
                <div class="headline-tag">HAFTANIN ANA MANŞETİ ${if (locationStr.isNotEmpty()) "• $locationStr" else ""}</div>
                <h1 class="headline-title">${news.title}</h1>
                <div class="headline-body">
                    $imgTag
                    <div class="headline-text">$excerptText</div>
                    <div class="continued-tag">👉 (Haberin Tamamı İç Sayfada)</div>
                </div>
            </div>
            """.trimIndent()
        } else ""

        val coverTeasersHtml = StringBuilder()
        for ((news, images) in coverItems) {
            val imgTag = if (images.isNotEmpty()) {
                "<img class='cover-teaser-img' src='${images[0]}' alt='${news.title}' />"
            } else ""

            val locationStr = if (news.province.isNotEmpty()) news.province.uppercase(locale) else ""
            val excerptText = news.description.take(140) + if (news.description.length > 140) "..." else ""

            coverTeasersHtml.append("""
                <article class="cover-teaser-card">
                    ${if (locationStr.isNotEmpty()) "<div class='article-location'>$locationStr</div>" else ""}
                    <h3 class="article-title">${news.title}</h3>
                    $imgTag
                    <div class="article-text">$excerptText</div>
                    <div class="continued-tag">👉 (Devamı İç Sayfada)</div>
                </article>
            """.trimIndent())
        }

        val coverColumnistHtml = if (columnists.isNotEmpty()) {
            val firstCol = columnists.first()
            val col = firstCol.columnist
            val avatarTag = if (firstCol.writerImageBase64 != null) {
                "<img class='cover-writer-avatar' src='${firstCol.writerImageBase64}' alt='${col.writerName}' />"
            } else "<span class='cover-writer-placeholder'>✍️</span>"
            val colExcerpt = col.content.take(120) + if (col.content.length > 120) "..." else ""
            """
            <div class="cover-columnist-highlight">
                <div class="cover-columnist-header">
                    $avatarTag
                    <div>
                        <span class="cover-writer-name">${col.writerName.uppercase(locale)}</span>
                        <span class="cover-writer-tag">• GÜNÜN KÖŞE YAZISI</span>
                    </div>
                </div>
                <div class="cover-columnist-body">
                    <strong>${col.title}:</strong> $colExcerpt (Devamı İç Sayfada)
                </div>
            </div>
            """.trimIndent()
        } else ""

        val firstAd = options.ads.firstOrNull()
        val coverAdHtml = if (firstAd != null) {
            val adImgTag = if (!firstAd.imageBase64.isNullOrEmpty()) {
                "<img class='ad-img-small' src='${firstAd.imageBase64}' alt='Reklam' />"
            } else ""
            """
            <div class="ad-box-compact">
                <div>
                    <span class="ad-header">${firstAd.title}</span>
                    <span class="ad-desc">${firstAd.description}</span>
                </div>
                $adImgTag
                <span class="ad-phone">İletişim: ${firstAd.phone}</span>
            </div>
            """.trimIndent()
        } else ""

        // INSIDE PAGES HTML GENERATION WITH EXPLICIT PAGE CHUNKING
        val columnistsHtml = StringBuilder()
        if (columnists.isNotEmpty()) {
            columnistsHtml.append("""
                <div class="columnists-section">
                    <div class="section-title-banner">✍️ KÖŞE YAZARLARIMIZ</div>
            """.trimIndent())

            for (item in columnists) {
                val col = item.columnist
                val avatarTag = if (item.writerImageBase64 != null) {
                    "<img class='writer-avatar' src='${item.writerImageBase64}' alt='${col.writerName}' />"
                } else "<div class='writer-avatar-placeholder'>✍️</div>"

                val contentImgTag = if (item.contentImageBase64 != null) {
                    "<img class='article-img' src='${item.contentImageBase64}' alt='${col.title}' />"
                } else ""

                columnistsHtml.append("""
                    <article class="columnist-card">
                        <div class="writer-header">
                            $avatarTag
                            <div class="writer-info">
                                <div class="writer-name">${col.writerName.uppercase(locale)}</div>
                                <div class="writer-subtitle">Köşe Yazısı</div>
                            </div>
                        </div>
                        <h3 class="columnist-title">${col.title}</h3>
                        $contentImgTag
                        <div class="columnist-text">${col.content.replace("\n", "<br/>")}</div>
                    </article>
                """.trimIndent())
            }

            columnistsHtml.append("</div>")
        }

        // Group articles into explicit pages so every page renders full news cleanly
        val articlesPerPage = if (isA3) 3 else 2
        var remainingArticles = newsWithImages.toList()
        var currentAdIndex = 1 // Ad #0 was on cover page

        val insidePagesHtml = StringBuilder()
        var pageNumber = 2

        // Page 2: Columnists + 1st batch of articles + Ad #2
        val page1ArticlesCount = if (columnists.isNotEmpty()) (articlesPerPage - 1).coerceAtLeast(1) else articlesPerPage
        val page1Articles = remainingArticles.take(page1ArticlesCount)
        remainingArticles = remainingArticles.drop(page1ArticlesCount)

        val page1AdHtml = if (currentAdIndex < options.ads.size) buildAdHtml(options.ads[currentAdIndex++]) else ""

        insidePagesHtml.append("""
            <div class="inside-page">
                <header class="inside-header">
                    <span>DOĞRU HAVADİS GAZETESİ</span>
                    <span>HABER DETAYLARI VE KÖŞE YAZILARI</span>
                    <span>SAYFA $pageNumber</span>
                </header>

                <div class="columns-wrapper">
                    $columnistsHtml
                    $page1AdHtml
                    ${buildArticlesHtml(page1Articles, locale)}
                </div>

                <footer class="footer-bar">
                    <span>Doğru Havadis Yayın Grubu © ${Calendar.getInstance().get(Calendar.YEAR)}</span>
                    <span>www.dogruhavadis.com</span>
                    <span>SAYFA $pageNumber</span>
                </footer>
            </div>
        """.trimIndent())
        pageNumber++

        // Subsequent inside pages (Page 3, 4, 5...)
        while (remainingArticles.isNotEmpty() || currentAdIndex < options.ads.size) {
            val pageArticles = remainingArticles.take(articlesPerPage)
            remainingArticles = remainingArticles.drop(articlesPerPage)

            val pageAdHtml = if (currentAdIndex < options.ads.size) buildAdHtml(options.ads[currentAdIndex++]) else ""

            if (pageArticles.isEmpty() && pageAdHtml.isEmpty()) break

            insidePagesHtml.append("""
                <div class="inside-page">
                    <header class="inside-header">
                        <span>DOĞRU HAVADİS GAZETESİ</span>
                        <span>BÖLGE VE GÜNDEM HABERLERİ</span>
                        <span>SAYFA $pageNumber</span>
                    </header>

                    <div class="columns-wrapper">
                        $pageAdHtml
                        ${buildArticlesHtml(pageArticles, locale)}
                    </div>

                    <footer class="footer-bar">
                        <span>Doğru Havadis Yayın Grubu © ${Calendar.getInstance().get(Calendar.YEAR)}</span>
                        <span>www.dogruhavadis.com</span>
                        <span>SAYFA $pageNumber</span>
                    </footer>
                </div>
            """.trimIndent())
            pageNumber++
        }

        return """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
            <meta charset="UTF-8">
            <style>
                @page {
                    size: $pageSizeCss;
                    margin: 8mm 10mm 10mm 10mm;
                }
                * {
                    box-sizing: border-box;
                }
                body {
                    font-family: 'Times New Roman', Times, serif;
                    color: #111;
                    margin: 0;
                    padding: 0;
                    background-color: #fff;
                    -webkit-print-color-adjust: exact;
                }

                /* Cover Page Layout */
                .cover-page {
                    page-break-after: always;
                    break-after: page;
                    box-sizing: border-box;
                    width: 100%;
                    overflow: hidden;
                }

                /* Inside Page Layout */
                .inside-page {
                    page-break-after: always;
                    break-after: page;
                    box-sizing: border-box;
                    width: 100%;
                }
                .inside-page:last-child {
                    page-break-after: auto;
                    break-after: auto;
                }

                /* Surmanset Banner */
                .surmanset-banner {
                    background-color: #111;
                    color: #fff;
                    font-size: 8.5pt;
                    padding: 3px 6px;
                    display: flex;
                    align-items: center;
                    margin-bottom: 5px;
                }
                .surmanset-tag {
                    background-color: #d32f2f;
                    color: #fff;
                    font-weight: bold;
                    padding: 1px 4px;
                    font-size: 7pt;
                    margin-right: 6px;
                    letter-spacing: 0.5px;
                }
                .surmanset-text {
                    font-weight: bold;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                
                /* Newspaper Header Masthead */
                .newspaper-header {
                    text-align: center;
                    border-bottom: 4px double #111;
                    padding-bottom: 3px;
                    margin-bottom: 6px;
                }
                .masthead-top-bar {
                    display: flex;
                    justify-content: space-between;
                    font-size: 8pt;
                    font-weight: bold;
                    border-bottom: 1px solid #111;
                    padding-bottom: 2px;
                    margin-bottom: 3px;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                }
                .newspaper-logo {
                    font-size: 32pt;
                    font-weight: 900;
                    letter-spacing: 2px;
                    text-transform: uppercase;
                    margin: 1px 0;
                    font-family: 'Georgia', serif;
                }
                .newspaper-subbar {
                    display: flex;
                    justify-content: space-between;
                    font-style: italic;
                    font-size: 8.5pt;
                    color: #222;
                    border-top: 1px solid #111;
                    padding-top: 2px;
                    margin-top: 2px;
                }

                /* Inside Header */
                .inside-header {
                    text-align: center;
                    border-bottom: 2px solid #111;
                    padding-bottom: 4px;
                    margin-bottom: 10px;
                    font-size: 9pt;
                    font-weight: bold;
                    letter-spacing: 1px;
                    display: flex;
                    justify-content: space-between;
                    text-transform: uppercase;
                }

                /* Headline Styling */
                .headline-container {
                    border: 2px solid #111;
                    padding: 6px;
                    margin-bottom: 8px;
                    background-color: #fafafa;
                    overflow: hidden;
                }
                .headline-tag {
                    background-color: #d32f2f;
                    color: #fff;
                    display: inline-block;
                    padding: 2px 6px;
                    font-size: 7.5pt;
                    font-weight: bold;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                    margin-bottom: 4px;
                }
                .headline-title {
                    font-size: 18pt;
                    line-height: 1.18;
                    margin: 0 0 4px 0;
                    font-weight: bold;
                    font-family: 'Georgia', serif;
                }
                .headline-body {
                    overflow: hidden;
                    display: block;
                }
                .headline-img {
                    width: 44%;
                    max-height: 180px;
                    object-fit: cover;
                    float: left;
                    margin-right: 10px;
                    margin-bottom: 4px;
                    border: 1px solid #ddd;
                }
                .headline-text {
                    font-size: 9pt;
                    line-height: 1.32;
                    text-align: justify;
                }

                /* Continued Tag Prompts */
                .continued-tag {
                    font-size: 8pt;
                    font-weight: bold;
                    color: #c62828;
                    margin-top: 4px;
                    text-align: right;
                    font-style: italic;
                    clear: both;
                }

                /* Layout Multi-Columns for Inside Pages */
                .columns-wrapper {
                    column-count: $columnCountCss;
                    column-gap: 16px;
                    column-rule: 1px solid #ccc;
                    width: 100%;
                    box-sizing: border-box;
                }

                /* Cover Teasers Grid (2-column Grid for Cover Page) */
                .cover-teasers-grid {
                    display: grid;
                    grid-template-columns: repeat(2, 1fr);
                    gap: 8px;
                    margin-bottom: 8px;
                }
                .cover-teaser-card {
                    box-sizing: border-box;
                    margin-bottom: 0;
                    padding: 6px;
                    border: 1px solid #ddd;
                    background-color: #fff;
                    break-inside: avoid;
                    -webkit-column-break-inside: avoid;
                    page-break-inside: avoid;
                    overflow: hidden;
                }
                .cover-teaser-img {
                    width: 100%;
                    max-height: 100px;
                    object-fit: cover;
                    margin-bottom: 4px;
                    border: 1px solid #eee;
                }

                /* Cover Columnist Highlight */
                .cover-columnist-highlight {
                    border: 1px solid #333;
                    background-color: #fdfbf7;
                    padding: 5px 8px;
                    margin-bottom: 8px;
                    overflow: hidden;
                }
                .cover-columnist-header {
                    display: flex;
                    align-items: center;
                    margin-bottom: 3px;
                }
                .cover-writer-avatar {
                    width: 24px;
                    height: 24px;
                    border-radius: 50%;
                    object-fit: cover;
                    margin-right: 6px;
                    border: 1px solid #111;
                }
                .cover-writer-placeholder {
                    font-size: 14px;
                    margin-right: 6px;
                }
                .cover-writer-name {
                    font-size: 8.5pt;
                    font-weight: bold;
                    color: #111;
                }
                .cover-writer-tag {
                    font-size: 7pt;
                    color: #d32f2f;
                    font-weight: bold;
                    margin-left: 4px;
                }
                .cover-columnist-body {
                    font-size: 8pt;
                    color: #222;
                    line-height: 1.25;
                }

                /* Article Cards inside pages - IMPORTANT CRITICAL FIX FOR CSS COLUMNS OVERLAP */
                .article-card {
                    break-inside: avoid-column;
                    -webkit-column-break-inside: avoid;
                    break-inside: avoid;
                    page-break-inside: avoid;
                    display: inline-block;
                    width: 100%;
                    margin-bottom: 14px;
                    padding-bottom: 10px;
                    border-bottom: 1px dashed #888;
                    box-sizing: border-box;
                    overflow: hidden;
                }
                .article-location {
                    font-size: 7.5pt;
                    font-weight: bold;
                    color: #c62828;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                }
                .article-title {
                    font-size: 12pt;
                    line-height: 1.2;
                    margin: 2px 0 4px 0;
                    font-weight: bold;
                    font-family: 'Georgia', serif;
                }
                .article-img {
                    width: 100%;
                    max-height: 160px;
                    object-fit: cover;
                    margin-bottom: 4px;
                    border: 1px solid #eee;
                    display: block;
                }
                .article-text {
                    font-size: 8.5pt;
                    line-height: 1.35;
                    text-align: justify;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                }

                /* Columnists Section - IMPORTANT CRITICAL FIX */
                .columnists-section {
                    break-inside: avoid-column;
                    -webkit-column-break-inside: avoid;
                    break-inside: avoid;
                    page-break-inside: avoid;
                    display: inline-block;
                    width: 100%;
                    margin-bottom: 14px;
                    background-color: #fdfbf7;
                    border: 2px solid #333;
                    padding: 6px;
                    box-sizing: border-box;
                }
                .section-title-banner {
                    background-color: #111;
                    color: #fff;
                    font-size: 10pt;
                    font-weight: bold;
                    text-align: center;
                    padding: 2px 5px;
                    letter-spacing: 1px;
                    text-transform: uppercase;
                    margin-bottom: 6px;
                }
                .columnist-card {
                    break-inside: avoid-column;
                    -webkit-column-break-inside: avoid;
                    break-inside: avoid;
                    page-break-inside: avoid;
                    display: inline-block;
                    width: 100%;
                    margin-bottom: 10px;
                    padding-bottom: 6px;
                    border-bottom: 1px solid #ddd;
                    box-sizing: border-box;
                    overflow: hidden;
                }
                .writer-header {
                    display: flex;
                    align-items: center;
                    margin-bottom: 4px;
                }
                .writer-avatar {
                    width: 36px;
                    height: 36px;
                    border-radius: 50%;
                    object-fit: cover;
                    border: 2px solid #111;
                    margin-right: 6px;
                }
                .writer-avatar-placeholder {
                    width: 36px;
                    height: 36px;
                    border-radius: 50%;
                    background-color: #eee;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 15px;
                    margin-right: 6px;
                    border: 1px solid #ccc;
                }
                .writer-info {
                    display: flex;
                    flex-direction: column;
                }
                .writer-name {
                    font-size: 9.5pt;
                    font-weight: bold;
                    letter-spacing: 0.5px;
                    color: #111;
                }
                .writer-subtitle {
                    font-size: 7pt;
                    color: #d32f2f;
                    text-transform: uppercase;
                    font-weight: bold;
                }
                .columnist-title {
                    font-size: 11pt;
                    font-weight: bold;
                    margin: 2px 0;
                    font-family: 'Georgia', serif;
                    color: #000;
                }
                .columnist-text {
                    font-size: 8pt;
                    line-height: 1.28;
                    text-align: justify;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                }

                /* Compact Ad Box for Cover */
                .ad-box-compact {
                    border: 1px solid #333;
                    background-color: #fffde7;
                    padding: 3px 6px;
                    font-size: 7.5pt;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-top: 6px;
                    overflow: hidden;
                }
                .ad-img-small {
                    max-height: 40px;
                    max-width: 80px;
                    object-fit: contain;
                    margin: 0 4px;
                }

                /* Full Ad Box for Inside Pages - IMPORTANT CRITICAL FIX */
                .ad-box {
                    break-inside: avoid-column;
                    -webkit-column-break-inside: avoid;
                    break-inside: avoid;
                    page-break-inside: avoid;
                    display: inline-block;
                    width: 100%;
                    box-sizing: border-box;
                    border: 2px dashed #333;
                    background-color: #fffde7;
                    padding: 6px;
                    text-align: center;
                    margin-bottom: 14px;
                    overflow: hidden;
                }
                .ad-header {
                    font-size: 9pt;
                    font-weight: bold;
                    background-color: #333;
                    color: #fff;
                    padding: 1px 5px;
                    display: inline-block;
                    text-transform: uppercase;
                    margin-bottom: 3px;
                }
                .ad-img {
                    max-width: 100%;
                    max-height: 120px;
                    object-fit: contain;
                    margin: 3px 0;
                    display: block;
                }
                .ad-desc {
                    font-size: 8pt;
                    margin: 2px 0;
                }
                .ad-phone {
                    font-size: 9pt;
                    font-weight: bold;
                    color: #000;
                }

                /* Footer Bar */
                .footer-bar {
                    break-inside: avoid;
                    -webkit-column-break-inside: avoid;
                    page-break-inside: avoid;
                    margin-top: 10px;
                    border-top: 2px solid #111;
                    padding-top: 3px;
                    font-size: 7.5pt;
                    display: flex;
                    justify-content: space-between;
                    color: #333;
                    font-weight: bold;
                    clear: both;
                }
            </style>
        </head>
        <body>

            <!-- PAGE 1: KAPAK SAYFASI (FRONT COVER) -->
            <div class="cover-page">
                $surmansetHtml

                <header class="newspaper-header">
                    <div class="masthead-top-bar">
                        <span>YIL: 14 • SAYI: 4280</span>
                        <span>FİYAT: 5.00 TL</span>
                        <span>HAVA DURUMU: BÖLGE GENELİ AÇIK 32°C</span>
                        <span>GÜNEYDOĞU ANADOLU BASINI</span>
                    </div>
                    <div class="newspaper-logo">DOĞRU HAVADİS</div>
                    <div class="newspaper-subbar">
                        <span>"Doğru Haber, Tarafsız Bakış"</span>
                        <span>YAYIN TARİHİ: $issueDateStr</span>
                        <span>www.dogruhavadis.com</span>
                    </div>
                </header>

                $headlineCoverHtml

                <div class="cover-teasers-grid">
                    $coverTeasersHtml
                </div>

                $coverColumnistHtml

                $coverAdHtml

                <footer class="footer-bar">
                    <span>DOĞRU HAVADİS HAFTALIK GAZETESİ</span>
                    <span>KAPAK SAYFASI</span>
                    <span>SAYFA 1</span>
                </footer>
            </div>

            <!-- INSIDE PAGES (HABER DETAYLARI VE KÖŞE YAZILARI) -->
            $insidePagesHtml

        </body>
        </html>
        """.trimIndent()
    }

    private fun renderHtmlToPdf(
        htmlContent: String,
        pageSize: String,
        callback: PdfCallback
    ) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        val isA3 = pageSize.equals("A3", ignoreCase = true)
        val mediaSize = if (isA3) PrintAttributes.MediaSize.ISO_A3 else PrintAttributes.MediaSize.ISO_A4

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                try {
                    val printAdapter = webView.createPrintDocumentAdapter("DogruHavadisGazete")
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(mediaSize)
                        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                        .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
                        .build()

                    val pdfDir = File(context.cacheDir, "pdfs")
                    pdfDir.mkdirs()
                    val pdfFile = File(pdfDir, "dogru_havadis_gazete_${System.currentTimeMillis()}.pdf")

                    PdfAdapterExporter.export(
                        printAdapter,
                        printAttributes,
                        pdfFile,
                        object : PdfAdapterExporter.ExportCallback {
                            override fun onSuccess(file: File) {
                                callback.onSuccess(file, htmlContent)
                            }

                            override fun onError(e: Exception) {
                                callback.onError(e)
                            }
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback.onError(e)
                }
            }
        }

        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
    }
}
