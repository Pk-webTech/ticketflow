package com.ticketflow.notification.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Generates the PNG QR code embedded in every confirmation email.
 *
 * <p>The QR encodes the BOOKING REFERENCE (e.g. {@code TF-8F3K2QD1}) and
 * nothing else — not a URL, not personal data. Two reasons: a printed or
 * screenshotted ticket contains no PII if lost, and gate staff scan it into
 * {@code GET /api/bookings/reference/{reference}} which is authenticated and
 * authoritative. Encoding a URL would make the ticket only as trustworthy as
 * whoever holds the link.
 *
 * <p>Error-correction level M tolerates ~15% damage, which matters because
 * these get scanned off cracked phone screens in bad lighting.
 */
@Service
public class QrCodeService {

    private static final int SIZE_PX = 300;

    public byte[] generatePng(String content) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.MARGIN, 2
            );

            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX, hints);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate QR code for '" + content + "'", ex);
        }
    }
}
