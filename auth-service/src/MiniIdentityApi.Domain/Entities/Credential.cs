namespace MiniIdentityApi.Domain.Entities;

public class Credential
{
    public string PasswordHash { get; private set; } = null!;
    public string Salt { get; private set; } = null!;
    public DateTime LastChangedAt { get; private set; }

    // Constructor sin parámetros para que Entity Framework materialice la entidad.
    private Credential() { }

    public Credential(string passwordHash, string salt)
    {
        PasswordHash = passwordHash;
        Salt = salt;
        LastChangedAt = DateTime.UtcNow;
    }

    public void ChangePassword(string newHash, string newSalt)
    {
        PasswordHash = newHash;
        Salt = newSalt;
        LastChangedAt = DateTime.UtcNow;
    }
}
