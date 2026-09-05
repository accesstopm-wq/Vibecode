V := GF(2)^3;
points := Filtered(AsList(V), v -> v <> Zero(V));
G := GL(3,2);

perms := List(Elements(G), g ->
  PermList(List(points, v -> Position(points, v * g)))
);

P := Group(perms);
Print("GL(3,2) order: ", Size(G), "\n");
Print("Permutation group order: ", Size(P), "\n");
Print("Elements as permutations in S7:\n\n");

for p in perms do
  Print(p, "\n");
od;
