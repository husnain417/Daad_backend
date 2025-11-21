package com.Daad.ecommerce.service;

import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.model.Vendor;
import com.Daad.ecommerce.repository.CategoryRepository;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ProductService {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private VendorRepository vendorRepository;

	// Intentionally not mapping categories from remote source per sync requirements

	@Autowired
	private BackblazeService backblazeService;

	private final RestTemplate restTemplate = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Fetches products from a vendor's website sync URL (expects a JSON array)
	 * and saves them as new products for the given vendor.
	 *
	 * The remote product JSON is expected to contain at least: name, price, category (id)
	 * Optional fields: description, gender, colorInventories (list), images (list of urls)
	 *
	 * Products without a valid category id will be skipped.
	 *
	 * @param userId vendor id to attach products to
	 * @return list of saved products
	 * @throws Exception when the fetch or mapping fails
	 */
	public List<Product> syncProductsFromVendorWebsite(String userId) throws Exception {
		Optional<Vendor> vendorOpt = vendorRepository.findByUserId(userId);
		if (vendorOpt.isEmpty()) {
			throw new IllegalArgumentException("Vendor not found: " + userId);
		}

		Vendor vendor = vendorOpt.get();
        String websiteSyncUrl = vendor.getWebsiteSyncUrl();

		String resp = restTemplate.getForObject(websiteSyncUrl, String.class);
		if (resp == null || resp.isBlank()) {
			log.warn("Empty response from vendor sync URL: {}", websiteSyncUrl);
			return new ArrayList<>();
		}

		List<Map<String, Object>> remoteProducts = objectMapper.readValue(resp, new TypeReference<>() {});
		List<Product> saved = new ArrayList<>();

		for (Map<String, Object> rp : remoteProducts) {
			try {
				String name = rp.get("name") != null ? rp.get("name").toString() : null;
				if (name == null || name.isBlank()) {
					log.debug("Skipping remote product without name: {}", rp);
					continue;
				}

				// reference id from remote source (WooCommerce id)
				String referenceId = rp.get("id") != null ? rp.get("id").toString() : null;
				if (referenceId == null || referenceId.isBlank()) {
					log.debug("Skipping remote product without remote id: {}", rp);
					continue;
				}

				// Try to find existing product by reference id
				Optional<Product> existingOpt = productRepository.findByReferenceId(referenceId);
				Product product = existingOpt.orElseGet(Product::new);
				product.setReferenceId(referenceId);
				product.setName(name);
				product.setDescription(rp.get("description") != null ? parseDescription(rp.get("description").toString()) : null);

				if (rp.get("price") != null) {
					try {
						product.setPrice(new BigDecimal(rp.get("price").toString()));
					} catch (Exception e) {
						log.warn("Invalid price for product {}: {}", name, rp.get("price"));
					}
				}

				// gender if provided
				if (rp.get("gender") != null) {
					product.setGender(rp.get("gender").toString());
				}

				// require category id (remote must provide category id that maps to existing category)
				// Per request: do not map categories from remote source. Leave category empty/null.
				product.setCategory(null);



				product.setStatus("awaiting_approval");
				product.setIsActive(true);

				// Ensure vendor info attached
				Product.Vendor pv = new Product.Vendor();
				pv.setId(vendor.getId());
				pv.setBusinessName(vendor.getBusinessName());
				pv.setBusinessType(vendor.getBusinessType());
				pv.setStatus(vendor.getStatus());
				pv.setRating(vendor.getRating());
				product.setVendor(pv);

				// Save product (insert or update)
				Product savedProduct = productRepository.save(product);

				// color inventories: expect structure [{"color":"Red","colorCode":"#ff0000","sizes":[{"size":"M","stock":10}]}]
				Object colorInvObj = rp.get("colorInventories");
				if (colorInvObj instanceof List) {
					List<?> colorList = (List<?>) colorInvObj;
					for (Object ciObj : colorList) {
						if (!(ciObj instanceof Map)) continue;
						Map<?,?> ci = (Map<?,?>) ciObj;
						String color = ci.get("color") != null ? ci.get("color").toString() : null;
						String colorCode = ci.get("colorCode") != null ? ci.get("colorCode").toString() : null;
						Object sizesObj = ci.get("sizes");
						if (sizesObj instanceof List) {
							List<?> sizes = (List<?>) sizesObj;
							for (Object sObj : sizes) {
								if (!(sObj instanceof Map)) continue;
								Map<?,?> s = (Map<?,?>) sObj;
								String size = s.get("size") != null ? s.get("size").toString() : null;
								int stock = 0;
								try { stock = s.get("stock") != null ? Integer.parseInt(s.get("stock").toString()) : 0; } catch (Exception ignored) {}
								if (color != null && size != null) {
									productRepository.addSizeToColor(savedProduct.getId(), color, colorCode, size, stock);
								}
							}
						}
					}
					// reload inventory counts in savedProduct by fetching from repo
					try { savedProduct = productRepository.findById(savedProduct.getId()).orElse(savedProduct); } catch (Exception ignored) {}
				}

				// images: only upload and save images when creating a new local product.
				if (existingOpt.isEmpty()) {
					Object imagesObj = rp.get("images");
					if (imagesObj instanceof List) {
						List<String> urls = new ArrayList<>();
						List<String> fileKeys = new ArrayList<>();
						for (Object iu : (List<?>) imagesObj) {
							if (iu == null) continue;
							// WooCommerce images objects may be maps with 'src'
							String imageUrl = null;
							if (iu instanceof Map) {
								Object src = ((Map<?,?>) iu).get("src");
								if (src != null) imageUrl = src.toString();
							} else {
								imageUrl = iu.toString();
							}
							if (imageUrl == null) continue;
							try {
								BackblazeService.UploadResult ur = backblazeService.uploadFromUrl(imageUrl, "products/" + savedProduct.getId());
								urls.add(ur.url);
								fileKeys.add(ur.key);
							} catch (Exception e) {
								log.warn("Failed to upload remote image {} for product {}: {}", imageUrl, savedProduct.getId(), e.getMessage());
							}
						}
						if (!urls.isEmpty()) {
							productRepository.saveProductImages(savedProduct.getId(), urls, null, fileKeys);
						}
					}
				}

				saved.add(savedProduct);
			} catch (Exception e) {
				log.error("Failed to sync a remote product: {}", e.getMessage(), e);
			}
		}

		return saved;
	}

    private String parseDescription(String description) {
        description = description.replaceAll("(?i)<(?!/?(p|li)\\\\b)[^>]+>", "");
        String regex = "(?i)<(p|li)[^>]*>(.*?)</\\1>|([^<>\\n]+)";
        Pattern pattern = Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
        Matcher matcher = pattern.matcher(description);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String text;
            if (matcher.group(2) != null) {
                text = matcher.group(2).trim(); // content inside <p> or <li>
            } else {
                text = matcher.group(3).trim(); // free text outside tags
            }

            if (!text.isEmpty()) {
                result.append(text).append("\n");
            }
        }

        return result.toString();
    }

}
