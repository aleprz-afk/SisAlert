namespace MiniIdentityApi.Application.Interfaces;

public interface IPasswordHasher
{
    string GenerateSalt();
    string Hash(string plainText, string salt);
    bool Verify(string plainText, string salt, string expectedHash);
}