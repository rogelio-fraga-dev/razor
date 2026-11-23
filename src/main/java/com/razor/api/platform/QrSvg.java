package com.razor.api.platform;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;

/**
 * QR code em SVG, gerado aqui (V56): a chave do 2FA nunca sai para um servico de QR de terceiros.
 * Um unico {@code <path>} com um quadrado por modulo — leve e nitido em qualquer tamanho.
 */
public final class QrSvg {

    private QrSvg() {
    }

    public static String of(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
            int w = m.getWidth();
            int h = m.getHeight();
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (m.get(x, y)) path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + w + " " + h
                    + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code do app autenticador\">"
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"" + path + "\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException("Nao consegui gerar o QR", e);
        }
    }
}
