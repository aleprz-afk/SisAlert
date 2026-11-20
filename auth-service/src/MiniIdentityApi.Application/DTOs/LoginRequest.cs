namespace MiniIdentityApi.Application.DTOs.Auth;

public class LoginRequest
{
    public string UsernameOrEmail { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}