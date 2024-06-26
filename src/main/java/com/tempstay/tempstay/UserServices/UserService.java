package com.tempstay.tempstay.UserServices;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.tempstay.tempstay.Models.EmailModel;
import com.tempstay.tempstay.Models.LoginModel;
import com.tempstay.tempstay.Models.OTPModel;
import com.tempstay.tempstay.Models.ResponseMessage;
import com.tempstay.tempstay.Models.ServiceProviderModel;
import com.tempstay.tempstay.Models.UpdateUserDetails;
import com.tempstay.tempstay.Models.UserModel;
import com.tempstay.tempstay.Repository.OtpRepo;
import com.tempstay.tempstay.Repository.ServiceProviderRepository;
import com.tempstay.tempstay.Repository.UserRepository;
import com.tempstay.tempstay.StaticInfo.OTPGenerator;

import jakarta.transaction.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@EnableScheduling
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceProviderRepository serviceProviderRepository;

    @Autowired
    private ResponseMessage responseMessage;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailModel emailModel;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OtpRepo otpRepo;

    

    public String hashPassword(String password) {
        String strong_salt = BCrypt.gensalt(10);
        String encyptedPassword = BCrypt.hashpw(password, strong_salt);
        return encyptedPassword;
    }

    @Scheduled(fixedRate = 60000)
    public void deleteExpiredRecords() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.MINUTES);
        // System.out.println(expiryTime);

        List<OTPModel> expiredRecords = otpRepo.findByCreatedAt(expiryTime);
        if (expiredRecords.size() != 0) {
            otpRepo.deleteAll(expiredRecords);
        }
    }

    public ResponseEntity<Object> generateOTPforTwoFAServiceProviderService(ServiceProviderModel serviceProviderModel) {
        try {
            int otpForTwoFA = OTPGenerator.generateRandom6DigitNumber();
            OTPModel otp = new OTPModel();

            otp.setEmail(serviceProviderModel.getEmail());
            otp.setOtp(otpForTwoFA);
            otp.setUseCase("login");
            otp.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));

            String response = emailService.sendSimpleMail(serviceProviderModel.getEmail(),
                    "Your OTP for Two-Factor Authentication is " + otpForTwoFA + " . It is valid only for 5 minutes.",
                    "OTP for Two-Factor Authentication");
            responseMessage.setSuccess(true);
            responseMessage.setMessage(response);
            otpRepo.save(otp);
            return ResponseEntity.ok().body(responseMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> generateOTPforTwoFAService(UserModel userModel) {
        try {
            int otpForTwoFA = OTPGenerator.generateRandom6DigitNumber();
            OTPModel otp = new OTPModel();

            otp.setEmail(userModel.getEmail());
            otp.setOtp(otpForTwoFA);
            otp.setUseCase("login");
            otp.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));

            String response = emailService.sendSimpleMail(userModel.getEmail(),
                    "Your OTP for Two-Factor Authentication is " + otpForTwoFA + " . It is valid only for 5 minutes.",
                    "OTP for Two-Factor Authentication");
            otpRepo.save(otp);
            responseMessage.setSuccess(true);
            responseMessage.setMessage(response);
            return ResponseEntity.ok().body(responseMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> userRegisterService(Object userOrService, BindingResult bindingResult, String role) {
        try {
            if (!bindingResult.hasErrors()) {
                if (bindingResult.hasFieldErrors()) {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Enter valid Email");
                    return ResponseEntity.ok().body(responseMessage);
                }

                if (role.equals("user")) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    UserModel userModel = objectMapper.convertValue(userOrService, UserModel.class);
                    UserModel userByEmail = userRepository.findByEmail(userModel.getEmail());
                    ServiceProviderModel serviceproviderByEmail = serviceProviderRepository
                            .findByEmail(userModel.getEmail());
                    if (userByEmail == null & serviceproviderByEmail == null) {
                        userModel.setPassword(hashPassword(userModel.getPassword()));
                        userRepository.save(userModel);
                        responseMessage.setSuccess(true);
                        responseMessage.setMessage("Account Created Successfully!");
                        responseMessage.setToken(authService.generateToken(userModel.getEmail()));
                        return ResponseEntity.ok().body(responseMessage);
                    } else {
                        responseMessage.setSuccess(false);
                        responseMessage.setMessage("User with this email already exists!");
                        responseMessage.setToken(null);
                        return ResponseEntity.badRequest().body(responseMessage);
                    }
                }

                else if (role.equals("serviceprovider")) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    ServiceProviderModel serviceProviderModel = objectMapper.convertValue(userOrService,
                            ServiceProviderModel.class);
                    ServiceProviderModel serviceProvierByEmail = serviceProviderRepository
                            .findByEmail(serviceProviderModel.getEmail());

                    if (serviceProvierByEmail == null) {
                        serviceProviderModel.setPassword(hashPassword(serviceProviderModel.getPassword()));
                        serviceProviderRepository.save(serviceProviderModel);
                        responseMessage.setSuccess(true);
                        responseMessage.setMessage("Account Created Successfully!");
                        responseMessage.setToken(authService.generateToken(serviceProviderModel.getEmail()));
                        return ResponseEntity.ok().body(responseMessage);
                    } else {
                        responseMessage.setSuccess(false);
                        responseMessage.setMessage("User with this email already exists!");
                        responseMessage.setToken(null);
                        return ResponseEntity.badRequest().body(responseMessage);
                    }
                }

                else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid Input!");
                    responseMessage.setToken(null);
                    return ResponseEntity.badRequest().body(responseMessage);
                }

            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid user name or email");
                return ResponseEntity.badRequest().body(responseMessage);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> userLoginService(LoginModel loginModel, String role) {
        try {
            if (role.equals("user")) {
                UserModel userModel = userRepository.findByEmail(loginModel.getEmail());
                if (userModel != null) {
                    if (BCrypt.checkpw(loginModel.getPassword(), userModel.getPassword())) {
                        responseMessage.setSuccess(true);
                        responseMessage.setMessage("Logged in Successfully!");
                        responseMessage.setToken(null);
                        generateOTPforTwoFAService(userModel);
                        return ResponseEntity.ok().body(responseMessage);
                    } else {
                        responseMessage.setSuccess(false);
                        responseMessage.setMessage("Invalid email or password");
                        responseMessage.setToken(null);
                        return ResponseEntity.badRequest().body(responseMessage);
                    }
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid email or password");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            } else {
                ServiceProviderModel serviceProviderModel = serviceProviderRepository
                        .findByEmail(loginModel.getEmail());

                if (serviceProviderModel != null) {
                    if (BCrypt.checkpw(loginModel.getPassword(), serviceProviderModel.getPassword())) {
                        responseMessage.setSuccess(true);
                        responseMessage.setMessage("Logged in Successfully!");
                        responseMessage.setToken(null);
                        generateOTPforTwoFAServiceProviderService(serviceProviderModel);
                        return ResponseEntity.ok().body(responseMessage);
                    } else {
                        responseMessage.setSuccess(false);
                        responseMessage.setMessage("Invalid email or password");
                        responseMessage.setToken(null);
                        return ResponseEntity.badRequest().body(responseMessage);
                    }
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid email or password");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error!");
        }
    }

    @Transactional
    public ResponseEntity<Object> TwoFAService(int otpforTwoFAFromUser, String email, String role) {
        try {
            int otpFromDB = otpRepo.findByEmail(email).getOtp();
            if (role.equals("user")) {
                if (otpFromDB == otpforTwoFAFromUser) {
                    responseMessage.setSuccess(true);
                    responseMessage.setMessage("Login Successfully!");
                    responseMessage.setToken(authService.generateToken(email));
                    otpRepo.deleteByEmail(email);
                    return ResponseEntity.ok().body(responseMessage);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid OTP.");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            } else {
                if (otpFromDB == otpforTwoFAFromUser) {
                    responseMessage.setSuccess(true);
                    responseMessage.setMessage("Login Successfully!");
                    responseMessage.setToken(authService.generateToken(email));
                    otpRepo.deleteByEmail(email);
                    return ResponseEntity.ok().body(responseMessage);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid OTP.");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            }

        } catch (Exception e) {
            responseMessage.setSuccess(false);
            responseMessage.setMessage("Internal Server Error! reason: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseMessage);
        }
    }

    public ResponseEntity<Object> forgotPasswordService(String email, String role) {
        return sendingEmailService(email, role);
    }

    public ResponseEntity<Object> sendingEmailService(String email, String role) {
        try {
            if (role.equals("user")) {
                UserModel userByEmail = userRepository.findByEmail(email);
                if (userByEmail != null) {
                    int otp = OTPGenerator.generateRandom6DigitNumber();
                    OTPModel otpForForgotPassword = new OTPModel();
                    otpForForgotPassword.setEmail(email);
                    otpForForgotPassword.setOtp(otp);
                    otpForForgotPassword.setUseCase("forgotpassword");
                    otpForForgotPassword.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));

                    otpRepo.save(otpForForgotPassword);

                    emailModel.setRecipient(email);
                    emailModel.setSubject("OTP for Resetting your password");
                    emailModel.setMsgBody("Your OTP for resetting your password is " + otp
                            + ". It is valid only for 5 minutes.");

                    String response = emailService.sendSimpleMail(email,
                            "Your OTP for resetting your password is " + Integer.toString(otp)
                                    + ". It is valid only for 5 minutes.",
                            "OTP for Resetting your password");
                    responseMessage.setSuccess(true);
                    responseMessage.setMessage(response);
                    return ResponseEntity.ok().body(responseMessage);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid Email");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            } else if (role.equals("serviceprovider")) {
                ServiceProviderModel spByEmail = serviceProviderRepository.findByEmail(email);
                if (spByEmail != null) {
                    int otp = OTPGenerator.generateRandom6DigitNumber();
                    OTPModel otpForForgotPassword = new OTPModel();
                    otpForForgotPassword.setEmail(email);
                    otpForForgotPassword.setOtp(otp);
                    otpForForgotPassword.setUseCase("forgotpassword");
                    otpForForgotPassword.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));

                    String response = emailService.sendSimpleMail(email,
                            "Your OTP for resetting your password is " + Integer.toString(otp)
                                    + ". It is valid only for 5 minutes.",
                            "OTP for Resetting your password");
                    responseMessage.setSuccess(true);
                    responseMessage.setMessage(response);
                    return ResponseEntity.ok().body(responseMessage);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid Email");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid role");
                return ResponseEntity.badRequest().body(responseMessage);
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> verifyTheOtpEnteredByUser(int otpFromUser, String email) {
        try {
            OTPModel otpFromDB = otpRepo.findByEmail(email);
            if (otpFromDB.getOtp() == otpFromUser) {
                responseMessage.setSuccess(true);
                responseMessage.setMessage("OTP Verified");
                return ResponseEntity.ok().body(responseMessage);
            } else {
                responseMessage.setSuccess(false);
                responseMessage.setMessage("Invalid OTP, check your registered Email to get the 6-digit OTP");
                return ResponseEntity.badRequest().body(responseMessage);
            }
        } catch (Exception e) {
            responseMessage.setSuccess(false);
            responseMessage.setMessage("Internal Server Error in verifyTheOtpEnteredByUser. Reason: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseMessage);
        }
    }

    public ResponseEntity<Object> resetThePasswordService(String passwordFromUser, String role, String email) {
        try {
            if (role.equals("user")) {
                UserModel user = userRepository.findByEmail(email);
                user.setPassword(hashPassword(passwordFromUser));
                userRepository.save(user);

                responseMessage.setSuccess(true);
                responseMessage.setMessage("Password Changed Successfully");
                responseMessage.setToken(null);
                otpRepo.deleteByEmail(email);
                return ResponseEntity.ok().body(responseMessage);
            } else {
                ServiceProviderModel serviceProviderModel = serviceProviderRepository
                        .findByEmail(email);
                serviceProviderModel.setPassword(hashPassword(passwordFromUser));
                serviceProviderRepository.save(serviceProviderModel);

                responseMessage.setSuccess(true);
                responseMessage.setMessage("Password Changed Successfully");
                responseMessage.setToken(null);
                otpRepo.deleteByEmail(email);
                return ResponseEntity.ok().body(responseMessage);
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<Object> getUserDetailsByEmailService(String token, String role) {
        try {
            String email = authService.verifyToken(token);
            if (role.equals("user")) {
                UserModel user = userRepository.findByEmail(email);
                if (user != null) {
                    return ResponseEntity.ok(user);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid email");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            } else {
                ServiceProviderModel serviceProvider = serviceProviderRepository.findByEmail(email);
                if (serviceProvider != null) {
                    return ResponseEntity.ok(serviceProvider);
                } else {
                    responseMessage.setSuccess(false);
                    responseMessage.setMessage("Invalid email");
                    return ResponseEntity.badRequest().body(responseMessage);
                }
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error!");
        }
    }

    public ResponseEntity<ResponseMessage> updateuserdetails(String token, UpdateUserDetails details) {
try{
        String email = authService.verifyToken(token);
        UserModel user = userRepository.findByEmail(email);
        UserModel updateToBeDone = userRepository.findByEmail(email);
        if (updateToBeDone == null) {
            responseMessage.setSuccess(false);
            responseMessage.setMessage("User with this email: " + email + " doesn't exist");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseMessage);
        } else {

            // user.setEmail(details.getEmail());
            user.setUserName(details.getUserName());
            user.setPhoneNumber(details.getPhoneNumber());

            userRepository.save(user);

            responseMessage.setSuccess(true);
            responseMessage.setMessage("Details updated successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseMessage);

        }

    }catch (Exception e) {
        responseMessage.setSuccess(false);
        responseMessage.setMessage(
                "Internal server error in UserService.java. Method: UserService. Reason: "
                        + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseMessage);
    }
}}
