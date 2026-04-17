package es.ual.dra.autodiagnostico.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import es.ual.dra.autodiagnostico.repository.VehicleRepository;

/**
 * Servicio encargado de realizar el scraping de datos de vehículos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UltimateSpecsVehicleScraperService {

        @Transactional
        public void scrapeAndSave() throws IOException {

                final String url = "https://www.ultimatespecs.com/es";

                System.out.println(">>> SCRAPER STARTED <<<");
                log.info("Iniciando scraping de la URL: {}", url);

                Document doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .timeout(10000)
                                .get();

                Elements brands = doc.select(".home_brands a[href]");

                File outputDir = new File("logos");
                outputDir.mkdirs();

                List<Map<String, Object>> allBrandsData = new ArrayList<>();

                for (Element brand : brands) {
                        Map<String, Object> brandData = new HashMap<>();

                        String brandName = brand.select(".home_brand").text();
                        Element img = brand.selectFirst(".home_brand_logo img");

                        brandData.put("brandName", brandName);

                        String spriteUrl = "";
                        int x = 0;
                        int y = 0;

                        if (img != null) {

                                String style = img.attr("style");

                                int urlStart = style.indexOf("url('");
                                int urlEnd = style.indexOf("')", urlStart);

                                if (urlStart != -1 && urlEnd != -1) {
                                        spriteUrl = style.substring(urlStart + 5, urlEnd);
                                }

                                String[] parts = style.split(" ");

                                for (String p : parts) {
                                        if (p.endsWith("px")) {
                                                int val = Math.abs(
                                                                Integer.parseInt(p.replace("px", "").replace(";", "")));

                                                if (x == 0)
                                                        x = val;
                                                else
                                                        y = val;
                                        }
                                }
                        }

                        if (!spriteUrl.isEmpty()) {

                                BufferedImage sprite = ImageIO.read(
                                                new URL("https://www.ultimatespecs.com" + spriteUrl));

                                BufferedImage logo = sprite.getSubimage(x, y, 60, 60);

                                File out = new File(outputDir,
                                                brandName.replaceAll("[^a-zA-Z0-9]", "_") + ".png");

                                ImageIO.write(logo, "png", out);
                        }

                        System.out.println("Brand: " + brandName);

                        // ===========================
                        // NEW: scrape models per brand
                        // ===========================
                        List<Map<String, Object>> modelsData = scrapeModelsForBrand(brand);
                        brandData.put("models", modelsData);
                        allBrandsData.add(brandData);
                }

                // Save all collected data to JSON
                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(new File("scraped_data.json"), allBrandsData);
                log.info("Data saved to scraped_data.json");
        }

        /**
         * SECOND LEVEL SCRAPER:
         * Extracts models from each brand page
         */
        private List<Map<String, Object>> scrapeModelsForBrand(Element brandElement) throws IOException {

                List<Map<String, Object>> modelsData = new ArrayList<>();
                Element brandLink = brandElement.parent().selectFirst("a[href]");
                if (brandLink == null) {
                        System.out.println("NO LINK");
                        return modelsData;
                }

                String brandUrl = brandLink.absUrl("href");

                if (brandUrl == null || brandUrl.isEmpty()) {
                        System.out.println("NO BRAND URL");
                        return modelsData;
                }

                log.info("Navigating to brand page: {}", brandUrl);

                Document doc = Jsoup.connect(brandUrl)
                                .userAgent("Mozilla/5.0")
                                .timeout(10000)
                                .get();

                Elements models = doc.select(".home_models_line a[href]");

                File outputDir = new File("models");
                outputDir.mkdirs();
                for (Element a : models) {
                        Map<String, Object> modelData = new HashMap<>();

                        Element img = a.selectFirst("img");
                        Element title = a.selectFirst("h2");

                        Element link = a.selectFirst("a[href]");
                        if (link == null) {
                                link = a; // The element itself is likely the link
                        }

                        String modelUrl = link.absUrl("href");
                        modelData.put("url", modelUrl);

                        Document modelDoc = Jsoup.connect(modelUrl)
                                        .userAgent("Mozilla/5.0")
                                        .timeout(10000)
                                        .get();

                        List<Map<String, Object>> versionsList = new ArrayList<>();

                        // A div with id versions exists only when this page is final
                        Element versionsDiv = modelDoc.selectFirst("#versions");

                        if (versionsDiv == null) {
                                System.out.println("TWO OR MORE VERSIONS, NOT FINAL PAGE");
                                Element modelVersions = modelDoc.select("a[href]");

                                for (Element version : modelVersions) {
                                        String versionUrl = version.absUrl("href");
                                        versionsList.add(scrapeVersion(versionUrl));
                                }
                        } else {
                                System.out.println("FINAL PAGE, SCRAPING VERSION");
                                versionsList.add(scrapeVersion(modelUrl));
                        }

                        modelData.put("versions", versionsList);

                        String modelName = title != null ? title.text() : "unknown";
                        modelData.put("modelName", modelName);

                        String imageUrl = "";
                        if (img != null) {
                                imageUrl = img.attr("abs:src");
                                if (imageUrl.isEmpty()) {
                                        imageUrl = "https:" + img.attr("src"); // fallback for // URLs
                                }
                        }
                        modelData.put("imageUrl", imageUrl);

                        System.out.println(modelName + " -> " + imageUrl);

                        if (!imageUrl.isEmpty()) {
                                downloadModelImage(imageUrl, brandUrl, modelName);
                        }
                        modelsData.add(modelData);
                }
                return modelsData;
        }

        private Map<String, Object> scrapeVersion(String urlFinalModel) {
                Map<String, Object> versionData = new HashMap<>();
                versionData.put("url", urlFinalModel);
                try {
                        Document doc = Jsoup.connect(urlFinalModel)
                                        .userAgent("Mozilla/5.0")
                                        .timeout(10000)
                                        .get();

                        Element img = doc.selectFirst(".left_column_top_model_image_div");

                        // I'm searching a div with class resumo_ficha
                        Element resumo_ficha = doc.selectFirst(".resumo_ficha");

                        if (resumo_ficha != null) {
                                // I need the div inside the div with class col-12 with a h2 with class
                                // post_title_12
                                Element specContainer = resumo_ficha.selectFirst(".margin-left:10px;margin-top:5px");

                                if (specContainer != null) {
                                        // Specifications should have a div with i class fa fa-dot-circle with bold text
                                        // with meaningful data
                                        Elements specsElements = specContainer.select(".fa-dot-circle");

                                        // Each specificatio nhas a bold text with the title of the specification and a
                                        // span with the actual value, which can have sup tags or similar
                                        Map<String, String> specsMap = new HashMap<>();
                                        for (Element specification : specsElements) {
                                                Element title = specification.selectFirst("b");
                                                Element value = specification.selectFirst("span");
                                                if (title != null && value != null) {
                                                        System.out.println(title.text() + " -> " + value.text());
                                                        specsMap.put(title.text(), value.text());
                                                }
                                        }
                                        versionData.put("specifications", specsMap);
                                }
                        }

                        // Search a div with class table_versions and extact all the table
                        // information
                        Element table_versions = doc.selectFirst(".table_versions");

                        if (table_versions != null) {
                                // The table has a thead with th elements with the headers
                                Element thead = table_versions.selectFirst("thead");
                                Elements headers = thead != null ? thead.select("th") : new Elements();

                                // The table has a tbody with tr elements with the data
                                Element tbody = table_versions.selectFirst("tbody");
                                Elements rows = tbody != null ? tbody.select("tr") : new Elements();

                                List<Map<String, String>> tableData = new ArrayList<>();
                                for (Element row : rows) {
                                        Elements cells = row.select("td");
                                        Map<String, String> rowData = new HashMap<>();
                                        for (int i = 0; i < headers.size() && i < cells.size(); i++) {
                                                System.out.println(headers.get(i).text() + " -> " + cells.get(i).text());
                                                rowData.put(headers.get(i).text(), cells.get(i).text());
                                        }
                                        tableData.add(rowData);
                                }
                                versionData.put("table_versions", tableData);
                        }

                } catch (IOException e) {
                        log.error("Error scraping version {}: {}", urlFinalModel, e.getMessage());
                }
                return versionData;
        }

        /**
         * Downloads model image
         */
        private void downloadModelImage(String imageUrl, String brandUrl, String modelName) {

                try {
                        BufferedImage image = ImageIO.read(new URL(imageUrl));

                        File dir = new File("models/" + sanitize(brandUrl));
                        dir.mkdirs();

                        File out = new File(dir, sanitize(modelName) + ".png");

                        ImageIO.write(image, "png", out);

                } catch (IOException e) {
                        log.error("Error downloading model image {}: {}", imageUrl, e.getMessage());
                }
        }

        /**
         * Safe filesystem naming
         */
        private String sanitize(String input) {
                return input.replaceAll("[^a-zA-Z0-9]", "_");
        }
}