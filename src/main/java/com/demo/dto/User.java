package com.demo.dto;

import com.demo.validation.NotAdminName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO used by every controller.
 * Serializable because @SessionAttributes stores it in the HTTP session.
 */
public class User implements Serializable {

    /*
     * VALIDATION GROUPS
     * A group is just an empty marker interface. It lets one class have different rules per use case.
     *   @Validated(OnCreate.class) -> checks OnCreate rules + Default rules (because it extends Default)
     *   @Validated(OnUpdate.class) -> checks OnUpdate rules + Default rules
     *   @Valid                     -> checks Default rules only
     */
    public interface OnCreate extends Default {}
    public interface OnUpdate extends Default {}

    // @Null (OnCreate group): on CREATE the client must NOT send an id. The server generates it.
    // @NotNull (OnUpdate group): on UPDATE the id is required.
    @Null(groups = OnCreate.class, message = "id must be empty when creating")
    @NotNull(groups = OnUpdate.class, message = "id is required when updating")
    private Long id;

    // @NotBlank: not null, and not just spaces.
    // @Size: length between min and max.
    // @NotAdminName: OUR custom constraint (see validation package).
    @NotBlank(message = "name is required")
    @Size(min = 2, max = 30, message = "name must be 2-30 chars")
    @NotAdminName
    private String name;

    // @Email: must look like an email address.
    @NotBlank(message = "email is required")
    @Email(message = "email format is invalid")
    private String email;

    // @Min: number must be >= 18.
    @Min(value = 18, message = "age must be 18+")
    private int age;

    // Clients should never set this. @InitBinder.setAllowedFields blocks it (mass-assignment protection).
    private String role = "USER";

    // Parsed from "dd-MM-yyyy" by the custom editor registered in @InitBinder (form binding only).
    private LocalDate joinDate;

    // @Valid on a field = CASCADE: also validate the rules inside Address.
    @Valid
    private Address address = new Address();

    public User() {}

    public User(Long id, String name, String email, int age) {
        this.id = id; this.name = name; this.email = email; this.age = age;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" + email + "', age=" + age
                + ", role='" + role + "', joinDate=" + joinDate + ", address=" + address + "}";
    }

    public static class Address implements Serializable {
        @NotBlank(message = "address.city is required")
        private String city;

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        @Override
        public String toString() { return "Address{city='" + city + "'}"; }
    }
}
