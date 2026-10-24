package com.azneotech.ordermanagementservice.clients;

import com.azneotech.ordermanagementservice.dtos.ProductCatalogDto;

public interface IProductCatalogClient {

    /**
     * @throws com.azneotech.ordermanagementservice.exceptions.ProductNotFoundException if the catalog has no such product
     * @throws com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException if product-catalog-service cannot answer
     */
    ProductCatalogDto getProduct(Long productId);

}
