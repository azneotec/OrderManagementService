package com.azneotech.ordermanagementservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** The slice of ProductCatalogService's {@code GET /products/{id}} response orders use; the rest is ignored. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductCatalogDto {

    private Long id;
    private String title;
    private BigDecimal price;
    private String image;

}
