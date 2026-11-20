using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace MiniIdentityApi.Infrastructure.Persistence;

/// <summary>
/// Fábrica usada solo por las herramientas de EF Core (`dotnet ef migrations ...`).
/// Evita tener que levantar toda la API para generar migraciones.
/// </summary>
public class IdentityDbContextFactory : IDesignTimeDbContextFactory<IdentityDbContext>
{
    public IdentityDbContext CreateDbContext(string[] args)
    {
        var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__Identity")
            ?? "Host=localhost;Port=5432;Database=sisalert;Username=sisalert;Password=sisalert";

        var options = new DbContextOptionsBuilder<IdentityDbContext>()
            .UseNpgsql(connectionString,
        npgsql => npgsql.MigrationsHistoryTable("__EFMigrationsHistory", IdentityDbContext.EsquemaHistorial))
            .Options;

        return new IdentityDbContext(options);
    }
}
