namespace MiniIdentityApi.Application.DTOs.Users;

public class UpdateUserRequest
{
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
}
