using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Application.DTOs.Users;

/// <summary>Vista segura de un usuario: nunca expone el hash ni la sal.</summary>
public class UserResponse
{
    public Guid Id { get; init; }
    public string Username { get; init; } = string.Empty;
    public string Email { get; init; } = string.Empty;
    public string Status { get; init; } = string.Empty;
    public List<string> Roles { get; init; } = new();

    public static UserResponse From(User user) => new()
    {
        Id = user.Id,
        Username = user.Username,
        Email = user.Email,
        Status = user.Status.ToString(),
        Roles = user.Roles.Select(r => r.Name).OrderBy(n => n).ToList()
    };
}
