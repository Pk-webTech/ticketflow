package com.ticketflow.notification.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    void producesAValidPng() throws Exception {
        byte[] png = service.generatePng("TF-ABCD1234");

        assertThat(png).isNotEmpty();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(300);
    }

    /** Round-trip: the ticket must actually be scannable back to the reference. */
    @Test
    void generatedCodeDecodesBackToTheBookingReference() throws Exception {
        String reference = "TF-8F3K2QD1";
        byte[] png = service.generatePng(reference);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(reference);
    }
}
