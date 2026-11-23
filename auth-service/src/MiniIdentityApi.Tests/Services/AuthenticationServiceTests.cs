using MiniIdentityApi.Application.DTOs.Auth;
using MiniIdentityApi.Application.Services;
using MiniIdentityApi.Infrastructure.Repositories;
using MiniIdentityApi.Infrastructure.Security;
using MiniIdentityApi.Tests.TestDoubles;
using Xunit;

namespace MiniIdentityApi.Tests.Services;

public class AuthenticationServiceTests
{
    [Fact]
    public void Register_ShouldSaveUser_WhenRequestIsValid()
    {
        // Arrange
        var userRepository = new InMemoryUserRepository();
        var passwordHasher = new Sha256PasswordHasher();
        var tokenService = new FakeTokenService();

        var service = new AuthenticationService(userRepository, passwordHasher, tokenService);

        var request = new RegisterRequest
        {
            Username = "wolfang",
            Email = "wolfang@example.com",
            Password = "Pass123!"
        };

        // Act
        service.Register(request);

        // Assert
        var savedUser = userRepository.FindByUsernameOrEmail("wolfang");
        Assert.NotNull(savedUser);
        Assert.Equal("wolfang", savedUser!.Username);
        Assert.Equal("wolfang@example.com", savedUser.Email);
    }

    [Fact]
    public void Register_ShouldThrowInvalidOperationException_WhenUserAlreadyExists()
    {
        // Arrange
        var userRepository = new InMemoryUserRepository();
        var passwordHasher = new Sha256PasswordHasher();
        var tokenService = new FakeTokenService();

        var service = new AuthenticationService(userRepository, passwordHasher, tokenService);

        var request = new RegisterRequest
        {
            Username = "wolfang",
            Email = "wolfang@example.com",
            Password = "Pass123!"
        };

        service.Register(request);

        // Act + Assert
        Assert.Throws<InvalidOperationException>(() => service.Register(request));
    }

    [Fact]
    public void Login_ShouldReturnToken_WhenCredentialsAreValid()
    {
        // Arrange
        var userRepository = new InMemoryUserRepository();
        var passwordHasher = new Sha256PasswordHasher();
        var tokenService = new FakeTokenService();

        var service = new AuthenticationService(userRepository, passwordHasher, tokenService);

        service.Register(new RegisterRequest
        {
            Username = "wolfang",
            Email = "wolfang@example.com",
            Password = "Pass123!"
        });

        var loginRequest = new LoginRequest
        {
            UsernameOrEmail = "wolfang",
            Password = "Pass123!"
        };

        // Act
        var response = service.Login(loginRequest);

        // Assert
        Assert.NotNull(response);
        Assert.Equal("wolfang", response.Username);
        Assert.Equal("wolfang@example.com", response.Email);
        Assert.False(string.IsNullOrWhiteSpace(response.AccessToken));
    }

    [Fact]
    public void Login_ShouldThrowUnauthorizedAccessException_WhenPasswordIsInvalid()
    {
        // Arrange
        var userRepository = new InMemoryUserRepository();
        var passwordHasher = new Sha256PasswordHasher();
        var tokenService = new FakeTokenService();

        var service = new AuthenticationService(userRepository, passwordHasher, tokenService);

        service.Register(new RegisterRequest
        {
            Username = "wolfang",
            Email = "wolfang@example.com",
            Password = "Pass123!"
        });

        var loginRequest = new LoginRequest
        {
            UsernameOrEmail = "wolfang",
            Password = "WrongPassword"
        };

        // Act + Assert
        Assert.Throws<UnauthorizedAccessException>(() => service.Login(loginRequest));
    }

    [Fact]
    public void Register_ShouldRejectShortPasswords()
    {
        var service = new AuthenticationService(
            new InMemoryUserRepository(), new Pbkdf2PasswordHasher(), new FakeTokenService());

        Assert.Throws<ArgumentException>(() => service.Register(new RegisterRequest
        {
            Username = "corta",
            Email = "corta@example.com",
            Password = "Ab1!"
        }));
    }

    [Fact]
    public void Login_ShouldMigrateLegacySha256Hash_ToPbkdf2()
    {
        // Usuario creado con el algoritmo anterior (SHA-256 + sal).
        var userRepository = new InMemoryUserRepository();
        new AuthenticationService(userRepository, new Sha256PasswordHasher(), new FakeTokenService())
            .Register(new RegisterRequest { Username = "viejo", Email = "viejo@example.com", Password = "Pass123!" });

        var service = new AuthenticationService(userRepository, new Pbkdf2PasswordHasher(), new FakeTokenService());

        service.Login(new LoginRequest { UsernameOrEmail = "viejo", Password = "Pass123!" });

        var user = userRepository.FindByUsernameOrEmail("viejo")!;
        Assert.StartsWith("pbkdf2-sha256$", user.Credential.PasswordHash);
        // Y sigue pudiendo entrar con la misma contraseña.
        Assert.NotNull(service.Login(new LoginRequest { UsernameOrEmail = "viejo", Password = "Pass123!" }));
    }

    [Fact]
    public void Login_WithWrongPassword_ShouldNotRevealThatUserIsInactive()
    {
        var userRepository = new InMemoryUserRepository();
        var service = new AuthenticationService(userRepository, new Pbkdf2PasswordHasher(), new FakeTokenService());
        service.Register(new RegisterRequest { Username = "inactivo", Email = "inactivo@example.com", Password = "Pass123!" });
        userRepository.FindByUsernameOrEmail("inactivo")!.Deactivate();

        var error = Assert.Throws<UnauthorizedAccessException>(() =>
            service.Login(new LoginRequest { UsernameOrEmail = "inactivo", Password = "WrongPassword" }));
        Assert.Equal("Invalid credentials.", error.Message);
    }
}
