namespace MiniIdentityApi.Domain.Entities;

public class Permission
{
    // Guid vacío hasta que EF Core lo genere al insertar. Así una nueva
    // instancia agregada a la colección de un rol se detecta como "Added"
    // y no como una fila existente que hay que actualizar.
    public Guid Id { get; private set; }
    public string Code { get; private set; } = null!;
    public string Description { get; private set; } = null!;

    // Constructor sin parámetros para que Entity Framework materialice la entidad.
    private Permission() { }

    public Permission(string code, string description)
    {
        Code = code;
        Description = description;
    }
}
