using System.Security.Cryptography;
using System.Text;
using MiniIdentityApi.Application.Interfaces;

namespace MiniIdentityApi.Infrastructure.Security;

/// <summary>
/// Hash de contraseñas con PBKDF2-HMAC-SHA256 (derivación lenta, resistente a fuerza bruta).
///
/// Formato guardado en PasswordHash: "pbkdf2-sha256$&lt;iteraciones&gt;$&lt;hash base64&gt;".
/// La sal sigue en su propia columna. Los hashes antiguos (SHA-256 + sal, sin prefijo)
/// se siguen aceptando y NeedsRehash indica que deben actualizarse en el próximo login.
/// </summary>
public class Pbkdf2PasswordHasher : IPasswordHasher
{
    private const string Prefijo = "pbkdf2-sha256";

    /// <summary>Iteraciones recomendadas por OWASP (2023) para PBKDF2-HMAC-SHA256.</summary>
    public const int Iteraciones = 600_000;

    private const int TamanoHashBytes = 32;

    private readonly Sha256PasswordHasher _legado = new();

    public string GenerateSalt()
    {
        return Convert.ToBase64String(RandomNumberGenerator.GetBytes(16));
    }

    public string Hash(string plainText, string salt)
    {
        var hash = Derivar(plainText, salt, Iteraciones);
        return $"{Prefijo}${Iteraciones}${Convert.ToBase64String(hash)}";
    }

    public bool Verify(string plainText, string salt, string expectedHash)
    {
        if (!expectedHash.StartsWith(Prefijo + "$", StringComparison.Ordinal))
        {
            // Hash anterior (SHA-256 + sal): se compara en tiempo constante.
            var legado = Encoding.UTF8.GetBytes(_legado.Hash(plainText, salt));
            return CryptographicOperations.FixedTimeEquals(legado, Encoding.UTF8.GetBytes(expectedHash));
        }

        var partes = expectedHash.Split('$');
        if (partes.Length != 3 || !int.TryParse(partes[1], out var iteraciones) || iteraciones <= 0)
            return false;

        byte[] esperado;
        try
        {
            esperado = Convert.FromBase64String(partes[2]);
        }
        catch (FormatException)
        {
            return false;
        }

        var calculado = Derivar(plainText, salt, iteraciones);
        return CryptographicOperations.FixedTimeEquals(calculado, esperado);
    }

    public bool NeedsRehash(string expectedHash)
    {
        if (!expectedHash.StartsWith(Prefijo + "$", StringComparison.Ordinal))
            return true;

        var partes = expectedHash.Split('$');
        return partes.Length != 3 || !int.TryParse(partes[1], out var iteraciones) || iteraciones < Iteraciones;
    }

    private static byte[] Derivar(string plainText, string salt, int iteraciones)
    {
        return Rfc2898DeriveBytes.Pbkdf2(
            Encoding.UTF8.GetBytes(plainText),
            Encoding.UTF8.GetBytes(salt),
            iteraciones,
            HashAlgorithmName.SHA256,
            TamanoHashBytes);
    }
}
