package com.azneotech.ordermanagementservice.clients;

import com.azneotech.ordermanagementservice.dtos.ProductCatalogDto;
import com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException;
import com.azneotech.ordermanagementservice.exceptions.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Calls ProductCatalogService by its Eureka name through the {@code @LoadBalanced} RestTemplate. */
@Service
public class ProductCatalogClient implements IProductCatalogClient {

    private static final String SERVICE = "product-catalog-service";
    private static final String PRODUCT_URL = "http://" + SERVICE + "/products/{id}";

    private final RestTemplate restTemplate;

    public ProductCatalogClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ProductCatalogDto getProduct(Long productId) {
        try {
            ProductCatalogDto product = restTemplate.getForObject(PRODUCT_URL, ProductCatalogDto.class, productId);
            if (product == null || product.getPrice() == null) {
                throw new DownstreamUnavailableException(SERVICE);
            }
            return product;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId);
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            // The load balancer throws IllegalStateException or IllegalArgumentException when no instance is registered.
            throw new DownstreamUnavailableException(SERVICE, e);
        }
    }

}
