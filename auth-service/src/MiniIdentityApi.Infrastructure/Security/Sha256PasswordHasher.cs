using System.Security.Cryptography;
using System.Text;
using MiniIdentityApi.Application.Interfaces;

namespace MiniIdentityApi.Infrastructure.Security;

public class Sha256PasswordHasher : IPasswordHasher
{
    public string GenerateSalt()
    {
        var bytes = RandomNumberGenerator.GetBytes(16);
        return Convert.ToBase64String(bytes);
    }

    public string Hash(string plainText, string salt)
    {
        using var sha256 = SHA256.Create();
        var bytes = Encoding.UTF8.GetBytes($"{plainText}{salt}");
        var hash = sha256.ComputeHash(bytes);
        return Convert.ToBase64String(hash);
    }

    public bool Verify(string plainText, string salt, string expectedHash)
    {
        var hash = Hash(plainText, salt);
        return hash == expectedHash;
    }
}