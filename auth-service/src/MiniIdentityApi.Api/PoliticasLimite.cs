namespace MiniIdentityApi.Api;

/// <summary>Nombres de las políticas de límite de peticiones.</summary>
public static class PoliticasLimite
{
    /// <summary>Login y registro: pocos intentos por minuto y por IP.</summary>
    public const string Autenticacion = "autenticacion";
}
