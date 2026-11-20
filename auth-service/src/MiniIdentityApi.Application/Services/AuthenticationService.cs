using MiniIdentityApi.Application.DTOs.Auth;
using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Entities;
using MiniIdentityApi.Domain.Enums;

namespace MiniIdentityApi.Application.Services;

public class AuthenticationService
{
    private readonly IUserRepository _userRepository;
    private readonly IPasswordHasher _passwordHasher;
    private readonly ITokenService _tokenService;

    public AuthenticationService(
        IUserRepository userRepository,
        IPasswordHasher passwordHasher,
        ITokenService tokenService)
    {
        _userRepository = userRepository;
        _passwordHasher = passwordHasher;
        _tokenService = tokenService;
    }

    public void Register(RegisterRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Username))
            throw new ArgumentException("Username is required.");

        if (string.IsNullOrWhiteSpace(request.Email))
            throw new ArgumentException("Email is required.");

        if (string.IsNullOrWhiteSpace(request.Password))
            throw new ArgumentException("Password is required.");

        var existing = _userRepository.FindByUsernameOrEmail(request.Username)
                      ?? _userRepository.FindByUsernameOrEmail(request.Email);

        if (existing is not null)
            throw new InvalidOperationException("User already exists.");

        var salt = _passwordHasher.GenerateSalt();
        var hash = _passwordHasher.Hash(request.Password, salt);

        var credential = new Credential(hash, salt);
        var user = new User(request.Username, request.Email, credential);

        _userRepository.Save(user);
    }

    public AuthResponse Login(LoginRequest request)
    {
        var user = _userRepository.FindByUsernameOrEmail(request.UsernameOrEmail);

        if (user is null)
            throw new UnauthorizedAccessException("Invalid credentials.");

        if (user.Status != UserStatus.Active)
            throw new UnauthorizedAccessException("User is not active.");

        var isValid = _passwordHasher.Verify(
            request.Password,
            user.Credential.Salt,
            user.Credential.PasswordHash);

        if (!isValid)
            throw new UnauthorizedAccessException("Invalid credentials.");

        return new AuthResponse
        {
            AccessToken = _tokenService.GenerateToken(user),
            Username = user.Username,
            Email = user.Email,
            Roles = user.Roles.Select(r => r.Name).ToList()
        };
    }
}