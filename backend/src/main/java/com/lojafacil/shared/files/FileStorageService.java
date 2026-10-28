package com.lojafacil.shared.files;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.error.Errors;

/**
 * Armazenamento local de imagens (logo, banner, fotos de produto).
 *
 * Medidas de segurança:
 * <ul>
 *   <li>Limite de tamanho (3 MB).</li>
 *   <li>Formato verificado pelos "magic bytes" do arquivo, não pela extensão ou content-type informados.</li>
 *   <li>A imagem é decodificada e regravada: isso descarta metadados (EXIF/GPS) e qualquer conteúdo
 *       embutido que não seja pixel, além de redimensionar imagens grandes (otimização).</li>
 *   <li>Nome do arquivo gerado pelo servidor (UUID), nunca o nome enviado pelo usuário.</li>
 * </ul>
 */
@Service
public class FileStorageService {

    public static final long MAX_BYTES = 3 * 1024 * 1024;
    public static final Pattern SAFE_NAME = Pattern.compile("^[0-9]+/[a-f0-9-]{36}\\.(jpg|png)$");

    private final Path root;

    public FileStorageService(AppProperties props) throws IOException {
        this.root = Paths.get(props.uploadsDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /**
     * Valida, otimiza e grava a imagem. Retorna o caminho relativo ("12/uuid.jpg").
     *
     * @param maxDimension maior lado permitido em pixels (a imagem é reduzida se exceder)
     */
    public String storeImage(Long storeId, MultipartFile file, int maxDimension) {
        if (file == null || file.isEmpty()) {
            throw Errors.badRequest("FILE_EMPTY", "Selecione um arquivo de imagem.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw Errors.badRequest("FILE_TOO_LARGE", "A imagem deve ter no máximo 3 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            String format = detectFormat(bytes);
            if (format == null) {
                throw Errors.badRequest("FILE_FORMAT", "Formato não suportado. Envie uma imagem JPG ou PNG.");
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 16 || image.getHeight() < 16) {
                throw Errors.badRequest("FILE_INVALID", "O arquivo não é uma imagem válida.");
            }
            if (image.getWidth() * (long) image.getHeight() > 40_000_000L) {
                throw Errors.badRequest("FILE_DIMENSIONS", "Imagem com resolução muito alta.");
            }
            BufferedImage output = resize(image, maxDimension, "png".equals(format));
            String name = storeId + "/" + UUID.randomUUID() + "." + ("png".equals(format) ? "png" : "jpg");
            Path target = resolve(name);
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(output, "png".equals(format) ? "png" : "jpg", target.toFile())) {
                throw new IOException("Sem writer para o formato");
            }
            return name;
        } catch (IOException e) {
            throw Errors.badRequest("FILE_INVALID", "Não foi possível processar a imagem enviada.");
        }
    }

    public static final long MAX_PHOTO_BYTES = 10 * 1024 * 1024;

    /**
     * Fotografia de fundo do painel: aceita até 10 MB na entrada, mas sempre grava um JPEG
     * progressivo (qualidade 0,8) com no máximo {@code maxDimension} px no maior lado.
     * Uma foto de celular de 6 MB costuma virar algo entre 250 e 500 KB.
     */
    public String storePhoto(Long storeId, MultipartFile file, int maxDimension) {
        if (file == null || file.isEmpty()) {
            throw Errors.badRequest("FILE_EMPTY", "Selecione uma fotografia.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw Errors.badRequest("FILE_TOO_LARGE", "A fotografia deve ter no máximo 10 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (detectFormat(bytes) == null) {
                throw Errors.badRequest("FILE_FORMAT", "Formato não suportado. Envie uma fotografia JPG ou PNG.");
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 640 || image.getHeight() < 360) {
                throw Errors.badRequest("FILE_DIMENSIONS", "Use uma fotografia de pelo menos 640 x 360 pixels.");
            }
            if (image.getWidth() * (long) image.getHeight() > 60_000_000L) {
                throw Errors.badRequest("FILE_DIMENSIONS", "Fotografia com resolução muito alta.");
            }
            BufferedImage output = resize(image, maxDimension, false);
            String name = storeId + "/" + UUID.randomUUID() + ".jpg";
            Path target = resolve(name);
            Files.createDirectories(target.getParent());
            writeJpeg(output, target, 0.8f);
            return name;
        } catch (IOException e) {
            throw Errors.badRequest("FILE_INVALID", "Não foi possível processar a fotografia enviada.");
        }
    }

    /** O caminho pertence à loja e o arquivo existe (impede apontar para imagens de outra loja). */
    public boolean belongsTo(Long storeId, String relativePath) {
        return relativePath != null && relativePath.startsWith(storeId + "/") && exists(relativePath);
    }

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        param.setProgressiveMode(javax.imageio.ImageWriteParam.MODE_DEFAULT);
        try (javax.imageio.stream.ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** Grava uma imagem gerada pelo próprio sistema (usado pelos dados demonstrativos). */
    public String storeRendered(Long storeId, BufferedImage image) {
        try {
            String name = storeId + "/" + UUID.randomUUID() + ".jpg";
            Path target = resolve(name);
            Files.createDirectories(target.getParent());
            ImageIO.write(image, "jpg", target.toFile());
            return name;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar imagem", e);
        }
    }

    public InputStream open(String relativePath) throws IOException {
        return Files.newInputStream(resolve(relativePath));
    }

    public boolean exists(String relativePath) {
        return SAFE_NAME.matcher(relativePath).matches() && Files.isRegularFile(resolve(relativePath));
    }

    public void delete(String relativePath) {
        if (relativePath == null || !SAFE_NAME.matcher(relativePath).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // arquivo órfão não impede a operação
        }
    }

    /** Resolve o caminho garantindo que ele permanece dentro da pasta de uploads (anti path traversal). */
    private Path resolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            throw Errors.badRequest("FILE_PATH", "Caminho inválido.");
        }
        return p;
    }

    static String detectFormat(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        return null;
    }

    private static BufferedImage resize(BufferedImage src, int maxDim, boolean keepAlpha) {
        double scale = Math.min(1.0, (double) maxDim / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (!keepAlpha) {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
        }
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
