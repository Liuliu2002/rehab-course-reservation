package com.rehab.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CourseDTO {
    private Long id;
    @NotBlank(message = "course name is required")
    private String name;
    @NotBlank(message = "course category is required")
    private String category;
    private String suitableCrowd;
    private String trainingGoal;
    @NotNull(message = "duration is required")
    @Min(value = 1, message = "duration must be positive")
    private Integer durationMinutes;
    @NotNull(message = "price is required")
    @DecimalMin(value = "0.00", message = "price cannot be negative")
    private BigDecimal price;
}
