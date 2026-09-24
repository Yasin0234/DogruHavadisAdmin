package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;

public class PdfAdapterExporter {

    public interface ExportCallback {
        void onSuccess(File file);
        void onError(Exception e);
    }

    public static void export(PrintDocumentAdapter adapter, PrintAttributes attributes, File pdfFile, ExportCallback callback) {
        adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                try {
                    ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
                    adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, descriptor, new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                        @Override
                        public void onWriteFinished(PageRange[] pages) {
                            try {
                                descriptor.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            callback.onSuccess(pdfFile);
                        }

                        @Override
                        public void onWriteFailed(CharSequence error) {
                            try {
                                descriptor.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            callback.onError(new Exception("PDF üretme hatası: " + error));
                        }
                    });
                } catch (Exception e) {
                    callback.onError(e);
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                callback.onError(new Exception("Düzen hatası: " + error));
            }
        }, null);
    }
}
