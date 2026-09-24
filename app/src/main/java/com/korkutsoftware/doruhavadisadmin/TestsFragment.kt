package com.korkutsoftware.doruhavadisadmin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class TestsFragment : Fragment() {

    private lateinit var ivPreview: ImageView
    private lateinit var etShareText: EditText
    private lateinit var btnFacebook: MaterialButton
    private lateinit var btnInstagram: MaterialButton

    private var imageUri: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_tests, container, false)

        ivPreview = view.findViewById(R.id.iv_share_preview)
        etShareText = view.findViewById(R.id.et_share_text)
        btnFacebook = view.findViewById(R.id.btn_share_facebook)
        btnInstagram = view.findViewById(R.id.btn_share_instagram)

        val imageUriString = arguments?.getString("imageUri")
        val shareText = arguments?.getString("shareText")

        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString)
            Glide.with(this).load(imageUri).into(ivPreview)
        }

        etShareText.setText(shareText)

        btnFacebook.setOnClickListener {
            shareToSocialMedia("com.facebook.katana", "Facebook")
        }

        btnInstagram.setOnClickListener {
            shareToSocialMedia("com.instagram.android", "Instagram")
        }

        return view
    }

    private fun shareToSocialMedia(packageName: String, platformName: String) {
        if (imageUri == null) {
            Toast.makeText(context, "Görüntü bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        val text = etShareText.text.toString()

        // Metni panoya kopyala (Facebook/Instagram bazen EXTRA_TEXT'i otomatik almıyor)
        copyToClipboard(text)
        Toast.makeText(context, "Metin kopyalandı, $platformName'da yapıştırabilirsiniz", Toast.LENGTH_LONG).show()

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, text)
                `package` = packageName
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Eğer uygulama yüklü değilse genel paylaşıcıyı aç
            Toast.makeText(context, "$platformName yüklü değil, alternatif paylaşım açılıyor", Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Haberi Paylaş"))
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Haber Metni", text)
        clipboard.setPrimaryClip(clip)
    }
}