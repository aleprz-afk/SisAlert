namespace MiniIdentityApi.Application.Interfaces;

public interface IPasswordHasher
{
    string GenerateSalt();
    string Hash(string plainText, string salt);
    bool Verify(string plainText, string salt, string expectedHash);

    /// <summary>
    /// Indica si el hash guardado usa un algoritmo o parámetros desactualizados y debe
    /// regenerarse (se hace en el login, cuando se conoce la contraseña en claro).
    /// </summary>
    bool NeedsRehash(string expectedHash) => false;
}