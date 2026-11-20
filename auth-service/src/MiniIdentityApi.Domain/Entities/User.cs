using MiniIdentityApi.Domain.Enums;

namespace MiniIdentityApi.Domain.Entities;

public class User
{
    public Guid Id { get; private set; } = Guid.NewGuid();
    public string Username { get; private set; } = null!;
    public string Email { get; private set; } = null!;
    public UserStatus Status { get; private set; } = UserStatus.Active;
    public Credential Credential { get; private set; } = null!;
    public List<Role> Roles { get; private set; } = new();

    // Constructor sin parámetros para que Entity Framework materialice la entidad.
    private User() { }

    public User(string username, string email, Credential credential)
    {
        Username = username;
        Email = email;
        Credential = credential;
    }

    public void Activate() => Status = UserStatus.Active;
    public void Deactivate() => Status = UserStatus.Inactive;
    public void Block() => Status = UserStatus.Blocked;

    public void UpdateProfile(string username, string email)
    {
        Username = username;
        Email = email;
    }

    public void AddRole(Role role)
    {
        if (Roles.Any(r => r.Name.Equals(role.Name, StringComparison.OrdinalIgnoreCase)))
            return;

        Roles.Add(role);
    }

    public bool HasPermission(string code)
    {
        return Roles.Any(role => role.Permissions.Any(p =>
            p.Code.Equals(code, StringComparison.OrdinalIgnoreCase)));
    }
}
