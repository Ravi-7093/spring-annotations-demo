package com.demo.web;

import com.demo.dto.User;
import com.demo.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/*
 * @Controller: a classic Spring MVC controller.
 *   A String return value is a VIEW NAME. The ViewResolver turns "user-form" into
 *   templates/user-form.html (Thymeleaf), fills it with the Model, and renders HTML.
 *
 * @RequestMapping("/users"): base path for all handlers here.
 *
 * @SessionAttributes("userForm"): any model attribute named "userForm" is ALSO stored in the
 *   HTTP session, so it survives across requests (multi-step form / wizard).
 *   Remove it with SessionStatus.setComplete() when the flow ends.
 *   Downside: server-side state. With many servers you need sticky sessions,
 *   Spring Session + Redis, or a stateless design (JWT).
 *
 * WIZARD FLOW (open in a browser):
 *   GET  /users/form     -> step 1: fill the form
 *   POST /users/form     -> validate, keep "userForm" in session, redirect to confirm
 *   GET  /users/confirm  -> step 2: show values read back from the SESSION
 *   POST /users/finish   -> save, clear the session attribute, show done page
 */
@Controller
@RequestMapping("/users")
@SessionAttributes("userForm")
public class UserViewController {

    private final UserService userService;

    public UserViewController(UserService userService) {
        this.userService = userService;
    }

    // ===================================================================================
    // @ModelAttribute on METHODS: run BEFORE every handler in this controller
    // ===================================================================================

    /*
     * @ModelAttribute("appVersion") on a method: its return value is added to the Model
     *   before EVERY handler in this controller. Templates can use ${appVersion}.
     *
     * CONSOLE (on every request to /users/**):
     *   [@ModelAttribute method] adding appVersion=1.0.0 to model
     */
    @ModelAttribute("appVersion")
    public String appVersion() {
        System.out.println("[@ModelAttribute method] adding appVersion=1.0.0 to model");
        return "1.0.0";
    }

    /*
     * Creates the empty form object ONLY if "userForm" is not already in the session.
     * (When @SessionAttributes already has it, Spring uses the session copy and skips this.)
     */
    @ModelAttribute("userForm")
    public User userForm() {
        System.out.println("[@ModelAttribute method] new empty userForm created (not in session yet)");
        return new User();
    }

    // ===================================================================================
    // @InitBinder: customize binding BEFORE request params are copied onto "userForm"
    // ===================================================================================

    /*
     * @InitBinder("userForm"): only applies when binding the "userForm" attribute.
     *
     * 1) setAllowedFields = WHITELIST. Any other request param is ignored.
     *    MASS ASSIGNMENT attack: attacker adds a hidden field role=ADMIN to the form.
     *    Since "role" is not allowed, it's dropped -> role stays "USER".
     *
     * 2) Custom editor: turns text "25-12-2024" (dd-MM-yyyy) into LocalDate 2024-12-25.
     *
     * CONSOLE on POST /users/form:
     *   [@InitBinder] allowed fields = name,email,age,joinDate,address.city
     *   [@InitBinder] date text '25-12-2024' -> LocalDate 2024-12-25
     */
    @InitBinder("userForm")
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields("name", "email", "age", "joinDate", "address.city");
        System.out.println("[@InitBinder] allowed fields = name,email,age,joinDate,address.city");

        binder.registerCustomEditor(LocalDate.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                LocalDate d = (text == null || text.isBlank()) ? null
                        : LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                System.out.println("[@InitBinder] date text '" + text + "' -> LocalDate " + d);
                setValue(d);
            }

            @Override
            public String getAsText() { // used when the form re-renders the value
                LocalDate d = (LocalDate) getValue();
                return d == null ? "" : d.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }
        });
    }

    // ===================================================================================
    // STEP 1: show + submit the form
    // ===================================================================================

    /*
     * Returns a VIEW NAME. ViewResolver -> templates/user-form.html.
     *
     * REQUEST:  open http://localhost:8080/users/form
     * RESPONSE: HTML form page
     */
    @GetMapping("/form")
    public String showForm() {
        return "user-form";
    }

    /*
     * @ModelAttribute("userForm") on a PARAMETER: build/reuse the "userForm" object and copy
     *   matching request params onto it via WebDataBinder (name -> setName, address.city -> getAddress().setCity).
     *   Reads FORM FIELDS / QUERY PARAMS. It does NOT read a JSON body (that's @RequestBody).
     *
     * @Valid: validate the bound object (Default group).
     * BindingResult: MUST come right after the validated parameter. It collects the errors
     *   instead of throwing, so we can re-show the form with messages.
     *   (Without BindingResult -> MethodArgumentNotValidException / 400.)
     *
     * REQUEST (form post):
     *   name=Kiran&email=kiran@test.com&age=25&joinDate=25-12-2024&address.city=Austin&role=ADMIN
     * CONSOLE:
     *   [@InitBinder] allowed fields = ...
     *   [@InitBinder] date text '25-12-2024' -> LocalDate 2024-12-25
     *   [@ModelAttribute] bound -> User{id=null, name='Kiran', email='kiran@test.com', age=25,
     *                              role='USER', joinDate=2024-12-25, address=Address{city='Austin'}}
     *                              ^^^^ role=ADMIN was BLOCKED
     *   [BindingResult] errors=0
     * RESPONSE: 302 redirect -> /users/confirm
     *
     * With age=15: [BindingResult] errors=1 -> age: age must be 18+ -> form re-rendered with message
     */
    @PostMapping("/form")
    public String submitForm(@Valid @ModelAttribute("userForm") User userForm,
                             BindingResult result) {
        System.out.println("[@ModelAttribute] bound -> " + userForm);
        System.out.println("[BindingResult] errors=" + result.getErrorCount());
        result.getFieldErrors().forEach(e ->
                System.out.println("   " + e.getField() + ": " + e.getDefaultMessage()));

        if (result.hasErrors()) {
            return "user-form";           // same view, Thymeleaf shows th:errors
        }
        return "redirect:/users/confirm"; // "redirect:" prefix = HTTP 302, not a view
    }

    // ===================================================================================
    // STEP 2: read back from SESSION
    // ===================================================================================

    /*
     * No form data is sent on this GET, yet userForm is filled: it came from the SESSION
     * thanks to @SessionAttributes("userForm").
     *
     * CONSOLE:  [@SessionAttributes] userForm from session -> User{id=null, name='Kiran', ...}
     */
    @GetMapping("/confirm")
    public String confirm(@ModelAttribute("userForm") User userForm) {
        System.out.println("[@SessionAttributes] userForm from session -> " + userForm);
        return "user-confirm";
    }

    /*
     * SessionStatus.setComplete(): flow finished -> remove @SessionAttributes entries from the session.
     *
     * CONSOLE:
     *   [finish] saving userForm from session -> User{...}
     *   [SessionStatus] setComplete() -> 'userForm' removed from session
     * RESPONSE: user-done.html showing the saved user with its new id
     */
    @PostMapping("/finish")
    public String finish(@ModelAttribute("userForm") User userForm, SessionStatus status, Model model) {
        System.out.println("[finish] saving userForm from session -> " + userForm);
        User saved = userService.create(userForm);
        status.setComplete();
        System.out.println("[SessionStatus] setComplete() -> 'userForm' removed from session");
        model.addAttribute("saved", saved); // Model = data handed to the view
        return "user-done";
    }

    // ===================================================================================
    // @SessionAttribute (singular): READ something someone else put in the session
    // ===================================================================================

    /*
     * Simulates a login: puts "loggedInUser" in the session directly.
     * REQUEST: open http://localhost:8080/users/fake-login  -> redirects to /users/whoami
     */
    @GetMapping("/fake-login")
    public String fakeLogin(HttpSession session) {
        session.setAttribute("loggedInUser", "ravi");
        System.out.println("[fake-login] session.setAttribute(loggedInUser, ravi)");
        return "redirect:/users/whoami";
    }

    /*
     * @SessionAttribute("loggedInUser"): read an EXISTING session attribute into a parameter.
     *   required = false -> null if not logged in (otherwise missing = 400).
     *
     * @ResponseBody on one method of a @Controller: skip the view, write JSON.
     *   (Answer to "Can a @Controller return JSON?" -> yes.)
     *
     * REQUEST:  /users/whoami (after /users/fake-login)
     * CONSOLE:  [@SessionAttribute] loggedInUser = ravi
     * RESPONSE: {"loggedInUser":"ravi"}      (before login: {"loggedInUser":"anonymous"})
     */
    @GetMapping("/whoami")
    @ResponseBody
    public Map<String, String> whoAmI(@SessionAttribute(name = "loggedInUser", required = false) String loggedIn) {
        System.out.println("[@SessionAttribute] loggedInUser = " + loggedIn);
        return Map.of("loggedInUser", loggedIn == null ? "anonymous" : loggedIn);
    }

    /*
     * Other way for @Controller to return JSON: return ResponseEntity<T> (no @ResponseBody needed).
     *
     * REQUEST:  curl -i localhost:8080/users/1/json
     * CONSOLE:  [@Controller + ResponseEntity] id=1
     * RESPONSE: 200, header X-Source: view-controller, {"id":1,"name":"Ravi",...}
     */
    @GetMapping("/{id}/json")
    public ResponseEntity<User> asJson(@PathVariable Long id) {
        System.out.println("[@Controller + ResponseEntity] id=" + id);
        return ResponseEntity.ok().header("X-Source", "view-controller").body(userService.findById(id));
    }
}
