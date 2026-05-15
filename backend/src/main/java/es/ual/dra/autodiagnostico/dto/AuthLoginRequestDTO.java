package es.ual.dra.autodiagnostico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthLoginRequestDTO {

    @NotBlank(message = "El correo es obligatorio")
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "El correo no tiene un formato valido")
    @Size(max = 180, message = "El correo excede el tamano permitido")
    private String email;

    @NotBlank(message = "La contrasena es obligatoria")
    private String password;
}
