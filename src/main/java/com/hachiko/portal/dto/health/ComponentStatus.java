package com.hachiko.portal.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estado de un componente individual del sistema (base de datos, JWT, email, etc.).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComponentStatus {

    private String name;
    private String status;
    private String message;
    private long responseTimeMs;
}
